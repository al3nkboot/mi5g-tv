package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * 2.4 M7: the family quiz's page for phones on the same Wi-Fi. Same rules as arrange-from-phone: home-network
 * clients only, a fresh 128-bit token in every path, small bodies, and it stops when the quiz screen closes.
 * Routes: GET /tok/ (the page), POST /tok/join {name}, POST /tok/answer {id,q,c}, GET /tok/state?id=.
 */
final class QuizServer {
    private static final String TAG = "Mi5G.Quiz";
    static final int MAX_BODY = 2048;

    static final class Reply {
        final int code;
        final String type;
        final byte[] body;
        Reply(int code, String type, byte[] body) { this.code = code; this.type = type; this.body = body; }
        static Reply json(JSONObject o) { return new Reply(200, "application/json; charset=utf-8", o.toString().getBytes(StandardCharsets.UTF_8)); }
        static Reply error(int code) { return new Reply(code, "text/plain", (code == 404 ? "not found" : "bad request").getBytes(StandardCharsets.US_ASCII)); }
    }

    private final Context ctx;
    private final FamilyQuiz quiz;
    final String token;
    private final ServerSocket socket;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private volatile boolean closed;
    private final java.util.Set<Socket> clients = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Called on a server thread after a join or an answer, so the TV can redraw. */
    volatile Runnable onChange;

    QuizServer(Context ctx, FamilyQuiz quiz, String token, ServerSocket socket) {
        this.ctx = ctx.getApplicationContext();
        this.quiz = quiz;
        this.token = token;
        this.socket = socket;
    }

    static QuizServer start(Context ctx, FamilyQuiz quiz) {
        try {
            QuizServer s = new QuizServer(ctx, quiz, ArrangeServer.newToken(new SecureRandom()), new ServerSocket(0));
            Thread t = new Thread(s::acceptLoop, "Mi5gQuiz");
            t.setDaemon(true);
            t.start();
            return s;
        } catch (IOException e) {
            Log.w(TAG, "cannot open a port: " + e);
            return null;
        }
    }

    String url() {
        String ip = ArrangeServer.localAddress();
        return ip == null ? null : "http://" + ip + ":" + socket.getLocalPort() + "/" + token + "/";
    }

    void stop() {
        if (closed) return;
        closed = true;
        for (Socket c : clients) try { c.close(); } catch (IOException ignored) { }
        clients.clear();
        try { socket.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket c = socket.accept();
                if (!ArrangeServer.localPeer(c.getInetAddress())) { c.close(); continue; }
                clients.add(c);
                pool.execute(() -> handle(c));
            } catch (IOException e) {
                if (!closed) Log.w(TAG, "accept: " + e);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                return;
            }
        }
    }

    private void handle(Socket c) {
        try (Socket s = c) {
            s.setSoTimeout(15_000);
            InputStream in = s.getInputStream();
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String first = line(in);
            Map<String, String> headers = new HashMap<>();
            String h;
            int count = 0;
            while ((h = line(in)) != null && !h.isEmpty() && count++ < 64) {
                int i = h.indexOf(':');
                if (i > 0) headers.put(h.substring(0, i).trim().toLowerCase(java.util.Locale.ROOT), h.substring(i + 1).trim());
            }
            ArrangeServer.Request r = ArrangeServer.parseLine(first, headers);
            Reply reply;
            if (r == null) reply = Reply.error(400);
            else {
                long length;
                try { length = Long.parseLong(headers.getOrDefault("content-length", "0")); } catch (NumberFormatException e) { length = -1; }
                byte[] body = "POST".equals(r.method) ? body(in, length) : new byte[0];
                reply = body == null ? Reply.error(400) : route(r.method, r.path, r.query, new String(body, StandardCharsets.UTF_8));
            }
            ArrangeServer.respond(out, reply.code, reply.type, reply.body);
        } catch (Exception e) {
            Log.w(TAG, "request: " + e);
        } finally {
            clients.remove(c);
        }
    }

    /** The routing, without sockets (tests). */
    Reply route(String method, String path, Map<String, String> query, String body) {
        String[] seg = path.split("/", 3);   // "", token, rest
        if (seg.length < 2 || !ArrangeServer.tokenMatches(token, seg[1])) return Reply.error(404);
        String rest = seg.length > 2 ? seg[2] : "";
        try {
            if ("GET".equals(method) && rest.isEmpty()) return new Reply(200, "text/html; charset=utf-8", page(ctx));
            if ("GET".equals(method) && "state".equals(rest)) return Reply.json(quiz.state(query.get("id")));
            if ("POST".equals(method) && "join".equals(rest)) {
                String id = quiz.join(new JSONObject(body).optString("name"));
                if (id == null) return Reply.json(new JSONObject().put("ok", false));
                changed();
                return Reply.json(new JSONObject().put("ok", true).put("id", id));
            }
            if ("POST".equals(method) && "answer".equals(rest)) {
                JSONObject o = new JSONObject(body);
                boolean ok = quiz.answer(o.optString("id"), o.optInt("q", -1), o.optInt("c", -1), System.currentTimeMillis());
                if (ok) changed();
                return Reply.json(new JSONObject().put("ok", ok));
            }
        } catch (org.json.JSONException e) {
            return Reply.error(400);
        }
        return Reply.error(404);
    }

    private void changed() { Runnable r = onChange; if (r != null) r.run(); }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 4096) throw new IOException("line too long");
        }
        return c == -1 && b.size() == 0 ? null : b.toString("UTF-8");
    }

    private static byte[] body(InputStream in, long length) throws IOException {
        if (length < 0 || length > MAX_BODY) return null;
        byte[] b = new byte[(int) length];
        int off = 0;
        while (off < b.length) {
            int n = in.read(b, off, b.length - off);
            if (n < 0) return null;
            off += n;
        }
        return b;
    }

    static String js(String s) { return JSONObject.quote(s); }

    /** The phone page: a name box, then four big answer buttons; it asks the TV for the state every second. */
    static byte[] page(Context c) {
        boolean rtl = Rows.rtl();
        String html = "<!doctype html><html lang='" + (rtl ? "ar" : "en") + "' dir='" + (rtl ? "rtl" : "ltr") + "'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>" + PhoneConnectActivity.esc(c.getString(R.string.mi5g_quiz)) + "</title>"
                + "<style>body{margin:0;font-family:system-ui,sans-serif;background:#0B1F1E;color:#fff;text-align:center;padding:18px}"
                + "h1{color:#D4AF37;font-size:22px}input,button{font:inherit;font-size:20px;border-radius:14px;border:0;padding:14px;width:100%;box-sizing:border-box;margin:6px 0}"
                + "#opts button{color:#fff;min-height:72px}.c0{background:#C0392B}.c1{background:#2E86C1}.c2{background:#239B56}.c3{background:#B7950B}"
                + "button:disabled{opacity:.45}.ok{outline:4px solid #fff}#msg{min-height:28px;color:#BFE9E3}#q{font-size:19px}</style></head><body>"
                + "<h1>" + PhoneConnectActivity.esc(c.getString(R.string.mi5g_quiz)) + "</h1>"
                + "<div id='join'><input id='name' maxlength='16' placeholder=" + js(c.getString(R.string.mi5g_quiz_name_hint)) + ">"
                + "<button id='go' style='background:#D4AF37;color:#0B1F1E'>" + PhoneConnectActivity.esc(c.getString(R.string.mi5g_quiz_join)) + "</button></div>"
                + "<div id='play' hidden><div id='me'></div><p id='q'></p><div id='opts'></div></div><p id='msg'></p>"
                + "<script>var T={wait:" + js(c.getString(R.string.mi5g_quiz_wait)) + ",sent:" + js(c.getString(R.string.mi5g_quiz_sent))
                + ",right:" + js(c.getString(R.string.mi5g_quiz_right)) + ",wrong:" + js(c.getString(R.string.mi5g_quiz_wrong))
                + ",taken:" + js(c.getString(R.string.mi5g_quiz_name_taken)) + ",done:" + js(c.getString(R.string.mi5g_quiz_over))
                + ",score:" + js(c.getString(R.string.mi5g_quiz_score_label)) + "};"
                + "var id=sessionStorage.getItem('quiz')||'',last='';function $(x){return document.getElementById(x)}"
                + "function post(p,o){return fetch(p,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(o)}).then(function(r){return r.json()})}"
                + "$('go').onclick=function(){post('join',{name:$('name').value}).then(function(r){if(r.ok){id=r.id;sessionStorage.setItem('quiz',id);tick()}else $('msg').textContent=T.taken})};"
                + "function tick(){fetch('state?id='+encodeURIComponent(id)).then(function(r){return r.json()}).then(draw).catch(function(){})}"
                + "function draw(s){$('join').hidden=s.joined;$('play').hidden=!s.joined;if(!s.joined)return;"
                + "$('me').textContent=s.name+'  •  '+T.score+' '+s.score;var key=s.phase+':'+s.q+':'+s.choice;"
                + "if(s.phase==0){$('q').textContent='';$('opts').innerHTML='';$('msg').textContent=T.wait;last=key;return}"
                + "if(s.phase==3){$('q').textContent='';$('opts').innerHTML='';$('msg').textContent=T.done+' #'+s.rank+'/'+s.players;last=key;return}"
                + "if(key==last)return;last=key;$('q').textContent=(s.q+1)+'/'+s.total+'  '+s.question;var o=$('opts');o.innerHTML='';"
                + "s.options.forEach(function(t,i){var b=document.createElement('button');b.textContent=t;b.className='c'+i;"
                + "if(s.phase==2&&i==s.answer)b.className+=' ok';b.disabled=s.phase!=1||s.choice>=0;"
                + "b.onclick=function(){post('answer',{id:id,q:s.q,c:i}).then(tick)};o.appendChild(b)});"
                + "$('msg').textContent=s.phase==2?(s.choice==s.answer?T.right+' +'+s.gained:T.wrong):(s.choice>=0?T.sent:'')}"
                + "setInterval(tick,1000);tick();</script></body></html>";
        return html.getBytes(StandardCharsets.UTF_8);
    }
}
