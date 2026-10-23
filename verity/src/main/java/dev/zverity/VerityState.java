package dev.zverity;

/** One transition per deliberate click, even when the client emits two packets. */
final class VerityState {
    enum Stage { BOX, OPENING, READY, FOLLOWING, REMOVED }
    enum Action { NONE, OPEN, GREET, KILL, BREAK_BOX }
    private Stage stage = Stage.BOX;
    private long lastClick = Long.MIN_VALUE / 2;

    Stage stage() { return stage; }
    void finishOpening() { if (stage == Stage.OPENING) stage = Stage.READY; }
    void remove() { stage = Stage.REMOVED; }

    Action click(long tick, boolean attack) {
        if (stage == Stage.REMOVED || stage == Stage.OPENING || tick - lastClick < 6) return Action.NONE;
        lastClick = tick;
        if (stage == Stage.BOX) {
            stage = attack ? Stage.REMOVED : Stage.OPENING;
            return attack ? Action.BREAK_BOX : Action.OPEN;
        }
        if (stage == Stage.READY) {
            stage = Stage.FOLLOWING;
            return Action.GREET;
        }
        stage = Stage.REMOVED;
        return Action.KILL;
    }
}
