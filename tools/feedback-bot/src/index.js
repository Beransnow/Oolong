/**
 * Collects feedback from the group. The bot keeps the group's messages for [KEEP_DAYS]; when the
 * owner reacts to one with [MARK], it is saved as feedback together with the message it answers
 * and the messages around it. Every few minutes a model writes up what was saved in a standard form,
 * with the screenshots that go with it (see [summarize]). Taking the reaction back drops it again
 * while it is still open.
 */

const MARK = "👀";
const KEEP_DAYS = 3;
// Messages before and after the marked one that are saved with it
const AROUND = 5;
// Hosted on Workers AI, within its free allowance; good at Chinese
const MODEL = "@cf/qwen/qwen3.8-27b";
// Screenshots shown to the model with one piece of feedback, at most
const IMAGES = 4;

export default {
    async fetch(request, env) {
        if (request.method !== "POST") return new Response("ok");
        if (!fromTelegram(request.headers.get("CF-Connecting-IP"))) {
            return new Response("forbidden", { status: 403 });
        }
        const update = await request.json();
        const message = update.message ?? update.edited_message;
        if (message && String(message.chat.id) === env.GROUP_ID) {
            await keep(env.DB, message);
        } else if (update.message_reaction && String(update.message_reaction.chat.id) === env.GROUP_ID) {
            await react(env.DB, update.message_reaction, env.OWNER_ID);
        }
        return new Response("ok");
    },

    async scheduled(controller, env) {
        const before = Math.floor(Date.now() / 1000) - KEEP_DAYS * 86400;
        await env.DB.prepare("DELETE FROM messages WHERE date < ?").bind(before).run();
        // Written up here rather than as it is marked: with screenshots to fetch and read, it takes
        // longer than a reply to Telegram may. One that fails is tried again on the next run.
        const { results } = await env.DB.prepare("SELECT message_id FROM feedback WHERE summary IS NULL LIMIT 10").all();
        for (const row of results) await summarize(env, row.message_id);
    },
};

// The addresses Telegram sends webhooks from
const TELEGRAM_NETWORKS = [["149.154.160.0", 20], ["91.108.4.0", 22]];

function fromTelegram(ip) {
    const value = (address) => address.split(".").reduce((n, part) => n * 256 + Number(part), 0);
    if (!ip || !/^\d+\.\d+\.\d+\.\d+$/.test(ip)) return false;
    return TELEGRAM_NETWORKS.some(([base, bits]) => {
        const size = 2 ** (32 - bits);
        return Math.floor(value(ip) / size) === Math.floor(value(base) / size);
    });
}

async function keep(db, message) {
    const from = message.from ?? {};
    const name = [from.first_name, from.last_name].filter(Boolean).join(" ") || from.username || null;
    const photo = message.photo?.at(-1)?.file_id ?? null;
    await db
        .prepare(
            `INSERT INTO messages (message_id, user_id, user_name, text, reply_to, photo, date)
             VALUES (?, ?, ?, ?, ?, ?, ?)
             ON CONFLICT (message_id) DO UPDATE SET text = excluded.text, photo = excluded.photo`,
        )
        .bind(
            message.message_id,
            from.id ?? null,
            name,
            message.text ?? message.caption ?? null,
            message.reply_to_message?.message_id ?? null,
            photo,
            message.date,
        )
        .run();
}

/** Saves or drops feedback as the owner marks a message or takes the mark back. */
async function react(db, reaction, ownerId) {
    if (String(reaction.user?.id) !== ownerId) return;
    const has = (list) => list.some((r) => r.type === "emoji" && r.emoji === MARK);
    const id = reaction.message_id;
    if (has(reaction.new_reaction) && !has(reaction.old_reaction)) {
        const context = await around(db, id);
        await db
            .prepare("INSERT OR IGNORE INTO feedback (message_id, marked_at, context) VALUES (?, ?, ?)")
            .bind(id, reaction.date, JSON.stringify(context))
            .run();
    } else if (!has(reaction.new_reaction) && has(reaction.old_reaction)) {
        await db.prepare("DELETE FROM feedback WHERE message_id = ? AND status = 'open'").bind(id).run();
    }
}

/** The marked message, the one it answers, and the messages kept just before and after it. */
async function around(db, id) {
    const columns = "message_id, user_id, user_name, text, reply_to, photo, date";
    const { results } = await db
        .prepare(
            `SELECT ${columns} FROM (SELECT ${columns} FROM messages WHERE message_id < ? ORDER BY message_id DESC LIMIT ?)
             UNION SELECT ${columns} FROM messages WHERE message_id = ?
             UNION SELECT ${columns} FROM (SELECT ${columns} FROM messages WHERE message_id > ? ORDER BY message_id LIMIT ?)
             UNION SELECT ${columns} FROM messages WHERE message_id = (SELECT reply_to FROM messages WHERE message_id = ?)
             ORDER BY message_id`,
        )
        .bind(id, AROUND, id, id, AROUND, id)
        .all();
    return results;
}

const PROMPT = `你在整理一款安卓贴吧客户端（Oolong，原名贴吧 Lite）用户群里的反馈。下面是群聊片段，带 ★ 的那条是被标记为反馈的消息，其余是上下文；附上的截图按［图片 n］的顺序对应。
只依据聊天内容和截图整理，不要推测或补充没有提到的信息；没提到的字段填空字符串或空数组。只输出一个 JSON 对象，不要其他文字：
{
  "kind": "问题" | "建议" | "疑问" | "其他",
  "title": "一句话标题，说清是什么问题或想要什么，不超过 30 字",
  "description": "两三句话描述现象或诉求",
  "steps": ["复现步骤，按顺序"],
  "expected": "期望的结果",
  "actual": "实际的结果",
  "version": "应用版本",
  "device": "机型",
  "system": "系统及版本，如 Android 15、HyperOS 2",
  "page": "涉及的页面或功能",
  "missing": ["对排查有用但没提到的信息，如 版本、机型、复现步骤、截图"]
}`;

/**
 * Has the model write up the feedback saved under [id] in a standard form, kept as JSON in its
 * summary along with who reported it and a link to the message. A failure is noted and tried again.
 */
async function summarize(env, id) {
    const row = await env.DB.prepare("SELECT context FROM feedback WHERE message_id = ?").bind(id).first();
    if (!row) return;
    const context = JSON.parse(row.context);
    const marked = context.find((m) => m.message_id === id);
    // The screenshots that belong with it: its own, the one it answers, and the reporter's around it
    const shown = context
        .filter((m) => m.photo && (m.message_id === id || m.message_id === marked?.reply_to || sameSender(m, marked)))
        .slice(0, IMAGES);
    const lines = context.map((m) => {
        const reply = m.reply_to ? `（回复 ${m.reply_to}）` : "";
        const index = shown.indexOf(m);
        const photo = !m.photo ? "" : index >= 0 ? `［图片 ${index + 1}］` : "［图片，未附上］";
        return `${m.message_id === id ? "★ " : ""}[${m.message_id}] ${m.user_name ?? "匿名"}${reply}：${photo}${m.text ?? ""}`;
    });
    try {
        const images = env.BOT_TOKEN ? await Promise.all(shown.map((m) => download(env.BOT_TOKEN, m.photo))) : [];
        const result = await env.AI.run(MODEL, {
            messages: [
                { role: "system", content: PROMPT },
                {
                    role: "user",
                    content: [
                        { type: "text", text: lines.join("\n") },
                        ...images.filter(Boolean).map((url) => ({ type: "image_url", image_url: { url } })),
                    ],
                },
            ],
            // Picking facts out needs little thinking
            reasoning_effort: "low",
            max_tokens: 2000,
        });
        const text = result.choices?.[0]?.message?.content ?? result.response ?? "";
        const summary = JSON.parse(text.slice(text.indexOf("{"), text.lastIndexOf("}") + 1));
        summary.reporter = context.find((m) => m.message_id === id)?.user_name ?? null;
        summary.link = `https://t.me/c/${env.GROUP_ID.replace(/^-100/, "")}/${id}`;
        await env.DB.prepare("UPDATE feedback SET summary = ?, summary_error = NULL WHERE message_id = ?")
            .bind(JSON.stringify(summary), id)
            .run();
    } catch (e) {
        await env.DB.prepare("UPDATE feedback SET summary_error = ? WHERE message_id = ?").bind(String(e).slice(0, 500), id).run();
    }
}

/** Whether [a] and [b] were sent by the same person; by name for feedback saved before ids were kept. */
function sameSender(a, b) {
    if (!b) return false;
    return b.user_id ? a.user_id === b.user_id : a.user_name === b.user_name;
}

/** The picture Telegram keeps under [fileId], as a data URL for the model, or null if it can't be had. */
async function download(token, fileId) {
    try {
        const file = await (await fetch(`https://api.telegram.org/bot${token}/getFile?file_id=${encodeURIComponent(fileId)}`)).json();
        if (!file.ok) return null;
        const bytes = new Uint8Array(await (await fetch(`https://api.telegram.org/file/bot${token}/${file.result.file_path}`)).arrayBuffer());
        let binary = "";
        for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
        return `data:image/jpeg;base64,${btoa(binary)}`;
    } catch {
        return null;
    }
}
