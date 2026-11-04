/**
 * Collects feedback from the group. The bot keeps the group's messages for [KEEP_DAYS]; when the
 * owner reacts to one with [MARK], it is saved as feedback together with the message it answers
 * and the messages around it. Taking the reaction back drops it again while it is still open.
 */

const MARK = "👀";
const KEEP_DAYS = 3;
// Messages before and after the marked one that are saved with it
const AROUND = 5;

export default {
    async fetch(request, env) {
        if (request.method !== "POST") return new Response("ok");
        if (request.headers.get("X-Telegram-Bot-Api-Secret-Token") !== env.WEBHOOK_SECRET) {
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
    },
};

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
    const columns = "message_id, user_name, text, reply_to, photo, date";
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
