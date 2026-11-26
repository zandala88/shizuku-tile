package io.github.local.shizukutoggle;

final class ToggleState {
    enum Actual { UNKNOWN, STOPPED, RUNNING }
    enum Command { START, STOP }
    private Actual actual = Actual.UNKNOWN;
    private Command pending;
    private long deadline;
    private String notice;

    Actual actual() { return actual; }
    Command pending() { return pending; }
    long deadline() { return deadline; }

    Command begin(boolean configured, long now) {
        if (pending != null || actual == Actual.UNKNOWN) return null;
        if (!configured) {
            notice = "请先填写 auth";
            return null;
        }
        pending = actual == Actual.RUNNING ? Command.STOP : Command.START;
        deadline = now + 60_000;
        notice = null;
        return pending;
    }

    void observe(Actual value, long now) {
        actual = value;
        // A late matching reply updates reality, but must not revive an expired operation.
        expire(now);
        if (pending != null && ((pending == Command.START && value == Actual.RUNNING)
                || (pending == Command.STOP && value == Actual.STOPPED))) {
            pending = null;
            deadline = 0;
            notice = null;
        }
    }

    void restore(Command command, long deadline, long now) {
        this.pending = command;
        this.deadline = deadline;
        expire(now);
    }

    private void expire(long now) {
        if (pending != null && now >= deadline) {
            notice = pending == Command.START ? "启动超时，请检查 Shizuku" : "停止超时，请检查 Shizuku";
            pending = null;
            deadline = 0;
        }
    }

    void fail(String message) {
        pending = null;
        deadline = 0;
        notice = message;
    }

    void clearNotice() { notice = null; }
    boolean hasNotice() { return notice != null; }

    String message() {
        if (pending != null) return pending == Command.START ? "正在启动…" : "正在停止…";
        if (notice != null) return notice;
        return switch (actual) {
            case UNKNOWN -> "正在检测…";
            case STOPPED -> "已停止 · 点按启动";
            case RUNNING -> "运行中 · 点按停止";
        };
    }
}
