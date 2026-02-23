/*
 * Frogget — Ribbit-cross lane ledger. Single deploy per runtime; frog position and lane state
 * are deterministic from config and tick. No token, no external oracle. Retro lane-crossing logic.
 */

package contracts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Frogget: retro frog-crossing-road game engine. All state and logic in one file.
 * Unique contract id and domain constants; constructor-set config; no fill-in data.
 */
public final class Frogget {

    // -------------------------------------------------------------------------
    // Contract identity (unique hex; not reused from any other contract)
    // -------------------------------------------------------------------------
    public static final String FROGGET_CONTRACT_ID = "0xc7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6";
    public static final String FROGGET_VERSION_HASH = "0x5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5";
    public static final String FROGGET_DOMAIN_SEED = "0xf0e1d2c3b4a5968778695a4b3c2d1e0f9a8b7c6d5e4";
    public static final int FROGGET_CHAIN_ID = 0x7e4a9f2c;
    public static final long FROGGET_GENESIS_TS = 0x63f01234L;

    // -------------------------------------------------------------------------
    // Game constants (distinct per contract)
    // -------------------------------------------------------------------------
    public static final int DEFAULT_LANES = 7;
    public static final int DEFAULT_COLS = 11;
    public static final int FROG_START_ROW = 8;
    public static final int INITIAL_LIVES = 3;
    public static final int TICKS_PER_MOVE = 4;
    public static final int MAX_LEVEL = 99;
    public static final int POINTS_PER_CROSS = 100;
    public static final int POINTS_LEVEL_BONUS = 50;
    public static final int OBSTACLE_SPAWN_DENOM = 5;
    public static final int SAFE_ZONE_TOP_ROW = 0;
    public static final int SAFE_ZONE_BOTTOM_ROW = 8;
    public static final int MIN_OBSTACLE_LEN = 2;
    public static final int MAX_OBSTACLE_LEN = 4;
    public static final int DIR_LEFT = -1;
    public static final int DIR_RIGHT = 1;

    public static final int SPRITE_FROG = 0;
    public static final int SPRITE_CAR_RED = 1;
    public static final int SPRITE_CAR_BLUE = 2;
    public static final int SPRITE_TRUCK = 3;
    public static final int SPRITE_LOG = 4;
    public static final int PALETTE_GREEN = 0x2d5016;
    public static final int PALETTE_ASPHALT = 0x1a1a1a;
    public static final int PALETTE_WHITE = 0xe8e8e8;
    public static final int PALETTE_FROG = 0x7cb342;
    public static final int RETRO_TILE_PX = 16;
    public static final int RETRO_FPS = 12;
    public static final int HIGH_SCORE_CAP = 10;

    // -------------------------------------------------------------------------
    // Error / event name constants (unique naming)
    // -------------------------------------------------------------------------
    public static final String FROGGET_ERR_BOUNDS = "FroggetOutOfBounds";
    public static final String FROGGET_ERR_COLLISION = "FroggetSquashed";
    public static final String FROGGET_ERR_NO_LIVES = "FroggetNoLivesLeft";
    public static final String FROGGET_ERR_INVALID_DIR = "FroggetInvalidDirection";
    public static final String FROGGET_ERR_GAME_OVER = "FroggetGameOver";
    public static final String FROGGET_EVT_LEVEL_UP = "FroggetLevelUp";
    public static final String FROGGET_EVT_CROSSED = "FroggetSafeCross";
    public static final String FROGGET_EVT_LIFE_LOST = "FroggetLifeLost";
    public static final String FROGGET_EVT_GAME_OVER = "FroggetGameOver";
    public static final String FROGGET_EVT_TICK = "FroggetTick";
    public static final String FROGGET_EVT_MOVE = "FroggetMove";

    // -------------------------------------------------------------------------
    // Immutable config (constructor-set)
    // -------------------------------------------------------------------------
    private final FroggetConfig config;
    private final Random rng;
    private FroggetState state;
    private int lastEventCode;
    private String lastEventName;

    public Frogget() {
        this.rng = new Random(Objects.hash(FROGGET_DOMAIN_SEED, System.nanoTime()));
        this.config = new FroggetConfig(
            DEFAULT_LANES,
            DEFAULT_COLS,
            FROG_START_ROW,
            INITIAL_LIVES,
            TICKS_PER_MOVE,
            MAX_LEVEL,
            POINTS_PER_CROSS,
            POINTS_LEVEL_BONUS,
            OBSTACLE_SPAWN_DENOM,
            MIN_OBSTACLE_LEN,
            MAX_OBSTACLE_LEN
        );
        this.state = FroggetState.initial(config);
        this.lastEventCode = 0;
        this.lastEventName = "";
    }

    public Frogget(final long seed) {
        this.rng = new Random(seed);
        this.config = new FroggetConfig(
            DEFAULT_LANES,
            DEFAULT_COLS,
            FROG_START_ROW,
            INITIAL_LIVES,
            TICKS_PER_MOVE,
            MAX_LEVEL,
            POINTS_PER_CROSS,
            POINTS_LEVEL_BONUS,
            OBSTACLE_SPAWN_DENOM,
            MIN_OBSTACLE_LEN,
            MAX_OBSTACLE_LEN
        );
        this.state = FroggetState.initial(config);
        this.lastEventCode = 0;
        this.lastEventName = "";
    }

    public FroggetConfig getConfig() {
        return config;
    }

    public FroggetState getState() {
        return state;
    }

    public int getLastEventCode() {
        return lastEventCode;
    }

    public String getLastEventName() {
        return lastEventName;
    }

    public void startNewGame() {
        state = FroggetState.initial(config);
        lastEventCode = 0;
        lastEventName = "";
        resetSessionStats();
    }

    public void moveFrog(final int dRow, final int dCol) {
        if (state.isGameOver()) {
            lastEventName = FROGGET_ERR_GAME_OVER;
            lastEventCode = -1;
            return;
        }
        if (state.isLevelComplete()) {
            return;
        }
        int nr = state.getFrogRow() + dRow;
        int nc = state.getFrogCol() + dCol;
        if (nr < 0 || nr > config.getRows() || nc < 0 || nc >= config.getCols()) {
            lastEventName = FROGGET_ERR_BOUNDS;
            lastEventCode = 1;
            return;
        }
        state.setFrogRow(nr);
        state.setFrogCol(nc);
        lastEventName = FROGGET_EVT_MOVE;
        lastEventCode = 2;
        if (nr == SAFE_ZONE_TOP_ROW) {
            state.addScore(config.getPointsPerCross() + state.getLevel() * 10);
            state.setLevelComplete(true);
            lastEventName = FROGGET_EVT_CROSSED;
            lastEventCode = 3;
        }
    }

    public void tick() {
        if (state.isGameOver()) return;
        if (state.isLevelComplete()) return;
        state.setTickCounter(state.getTickCounter() + 1);
        if (state.getTickCounter() % config.getTicksPerMove() != 0) {
            lastEventName = FROGGET_EVT_TICK;
            lastEventCode = 4;
            return;
        }
        updateLanes();
        if (checkCollision()) {
            state.setLives(state.getLives() - 1);
            lastEventName = FROGGET_EVT_LIFE_LOST;
            lastEventCode = 5;
            if (state.getLives() <= 0) {
                state.setGameOver(true);
                lastEventName = FROGGET_EVT_GAME_OVER;
                lastEventCode = 6;
            } else {
                resetFrogPosition();
            }
        }
    }

    public void advanceLevelIfComplete() {
        if (!state.isLevelComplete() || state.isGameOver()) return;
        state.setLevel(state.getLevel() + 1);
        state.setLevelComplete(false);
        state.addScore(config.getPointsLevelBonus());
        resetFrogPosition();
        state.setLanes(buildLanesForLevel(state.getLevel()));
        lastEventName = FROGGET_EVT_LEVEL_UP;
        lastEventCode = 7;
    }

    private void resetFrogPosition() {
        state.setFrogRow(config.getFrogStartRow());
        state.setFrogCol(config.getCols() / 2);
    }

    private void updateLanes() {
        List<FroggetLane> lanes = state.getLanes();
        for (FroggetLane lane : lanes) {
            int dir = lane.getDirection();
            for (FroggetObstacle ob : lane.getObstacles()) {
                ob.setCol(ob.getCol() + dir);
            }
            lane.getObstacles().removeIf(ob -> ob.getCol() + ob.getLength() < 0 || ob.getCol() >= config.getCols());
            if (rng.nextInt(config.getObstacleSpawnDenom()) == 0) {
                spawnObstacleInLane(lane);
            }
        }
    }

    private void spawnObstacleInLane(final FroggetLane lane) {
        int len = config.getMinObstacleLen() + rng.nextInt(config.getMaxObstacleLen() - config.getMinObstacleLen() + 1);
        int startCol = lane.getDirection() == DIR_RIGHT ? -len : config.getCols();
        FroggetObstacle ob = new FroggetObstacle(startCol, len, rng.nextInt(4));
        lane.getObstacles().add(ob);
    }

    private boolean checkCollision() {
        int fr = state.getFrogRow();
        int fc = state.getFrogCol();
        if (fr <= SAFE_ZONE_BOTTOM_ROW) return false;
        List<FroggetLane> lanes = state.getLanes();
        int laneIndex = fr - SAFE_ZONE_BOTTOM_ROW - 1;
        if (laneIndex < 0 || laneIndex >= lanes.size()) return false;
        FroggetLane lane = lanes.get(laneIndex);
        for (FroggetObstacle ob : lane.getObstacles()) {
            if (fc >= ob.getCol() && fc < ob.getCol() + ob.getLength()) return true;
        }
        return false;
    }

    private List<FroggetLane> buildLanesForLevel(final int level) {
        return buildLanesForLevelStatic(config, level, rng);
    }

    /** Static lane builder for a given config, level, and RNG. Used by initial state and advanceLevel. */
    public static List<FroggetLane> buildLanesForLevelStatic(final FroggetConfig config, final int level, final Random rng) {
        List<FroggetLane> lanes = new ArrayList<>();
        for (int r = 1; r <= config.getLanes(); r++) {
            int dir = (r + level) % 2 == 0 ? DIR_RIGHT : DIR_LEFT;
            int speed = 1 + (level / 3) + (r % 2);
            FroggetLane lane = new FroggetLane(r, dir, speed);
            for (int i = 0; i < 2 + level; i++) {
                spawnObstacleInLaneStatic(lane, config, rng);
            }
            lanes.add(lane);
        }
        return lanes;
    }

    private static void spawnObstacleInLaneStatic(final FroggetLane lane, final FroggetConfig config, final Random rng) {
        int len = config.getMinObstacleLen() + rng.nextInt(config.getMaxObstacleLen() - config.getMinObstacleLen() + 1);
        int startCol = lane.getDirection() == DIR_RIGHT ? -len : config.getCols();
        FroggetObstacle ob = new FroggetObstacle(startCol, len, rng.nextInt(4));
        lane.getObstacles().add(ob);
    }

    public String toGridString() {
        StringBuilder sb = new StringBuilder();
        int rows = config.getLanes() + 2;
        int cols = config.getCols();
        for (int r = 0; r <= rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r == state.getFrogRow() && c == state.getFrogCol()) {
                    sb.append('F');
                } else if (r == SAFE_ZONE_TOP_ROW || r == config.getFrogStartRow()) {
                    sb.append('.');
                } else {
                    int li = r - SAFE_ZONE_BOTTOM_ROW - 1;
                    boolean found = false;
                    if (li >= 0 && li < state.getLanes().size()) {
                        for (FroggetObstacle ob : state.getLanes().get(li).getObstacles()) {
                            if (c >= ob.getCol() && c < ob.getCol() + ob.getLength()) {
                                sb.append('X');
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) sb.append('.');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Inner: FroggetConfig (immutable)
    // -------------------------------------------------------------------------
    public static final class FroggetConfig {
        private final int lanes;
        private final int cols;
        private final int frogStartRow;
        private final int lives;
        private final int ticksPerMove;
        private final int maxLevel;
        private final int pointsPerCross;
        private final int pointsLevelBonus;
        private final int obstacleSpawnDenom;
        private final int minObstacleLen;
        private final int maxObstacleLen;

        public FroggetConfig(
            final int lanes,
            final int cols,
            final int frogStartRow,
            final int lives,
            final int ticksPerMove,
            final int maxLevel,
            final int pointsPerCross,
            final int pointsLevelBonus,
            final int obstacleSpawnDenom,
            final int minObstacleLen,
            final int maxObstacleLen
        ) {
            this.lanes = lanes;
            this.cols = cols;
            this.frogStartRow = frogStartRow;
            this.lives = lives;
            this.ticksPerMove = ticksPerMove;
            this.maxLevel = maxLevel;
            this.pointsPerCross = pointsPerCross;
            this.pointsLevelBonus = pointsLevelBonus;
            this.obstacleSpawnDenom = obstacleSpawnDenom;
            this.minObstacleLen = minObstacleLen;
            this.maxObstacleLen = maxObstacleLen;
        }

        public int getLanes() { return lanes; }
        public int getCols() { return cols; }
        public int getRows() { return lanes + 2; }
        public int getFrogStartRow() { return frogStartRow; }
        public int getLives() { return lives; }
        public int getTicksPerMove() { return ticksPerMove; }
        public int getMaxLevel() { return maxLevel; }
        public int getPointsPerCross() { return pointsPerCross; }
        public int getPointsLevelBonus() { return pointsLevelBonus; }
        public int getObstacleSpawnDenom() { return obstacleSpawnDenom; }
        public int getMinObstacleLen() { return minObstacleLen; }
        public int getMaxObstacleLen() { return maxObstacleLen; }
    }

    // -------------------------------------------------------------------------
    // Inner: FroggetState
    // -------------------------------------------------------------------------
    public static final class FroggetState {
        private int frogRow;
        private int frogCol;
        private int lives;
        private int score;
        private int level;
        private int tickCounter;
        private boolean gameOver;
        private boolean levelComplete;
        private List<FroggetLane> lanes;

        public static FroggetState initial(final FroggetConfig config) {
            FroggetState s = new FroggetState();
            s.frogRow = config.getFrogStartRow();
            s.frogCol = config.getCols() / 2;
            s.lives = config.getLives();
            s.score = 0;
            s.level = 1;
            s.tickCounter = 0;
            s.gameOver = false;
            s.levelComplete = false;
            s.lanes = Frogget.buildLanesForLevelStatic(config, 1, new Random(0));
            return s;
        }

        public int getFrogRow() { return frogRow; }
        public void setFrogRow(final int frogRow) { this.frogRow = frogRow; }
        public int getFrogCol() { return frogCol; }
        public void setFrogCol(final int frogCol) { this.frogCol = frogCol; }
        public int getLives() { return lives; }
        public void setLives(final int lives) { this.lives = lives; }
        public int getScore() { return score; }
        public void addScore(final int n) { this.score += n; }
        public int getLevel() { return level; }
        public void setLevel(final int level) { this.level = level; }
        public int getTickCounter() { return tickCounter; }
        public void setTickCounter(final int tickCounter) { this.tickCounter = tickCounter; }
        public boolean isGameOver() { return gameOver; }
        public void setGameOver(final boolean gameOver) { this.gameOver = gameOver; }
        public boolean isLevelComplete() { return levelComplete; }
        public void setLevelComplete(final boolean levelComplete) { this.levelComplete = levelComplete; }
        public List<FroggetLane> getLanes() { return lanes; }
        public void setLanes(final List<FroggetLane> lanes) { this.lanes = lanes; }

        public static FroggetState copyFrom(final FroggetState other) {
            FroggetState s = new FroggetState();
            s.frogRow = other.frogRow;
            s.frogCol = other.frogCol;
            s.lives = other.lives;
            s.score = other.score;
            s.level = other.level;
            s.tickCounter = other.tickCounter;
            s.gameOver = other.gameOver;
            s.levelComplete = other.levelComplete;
            s.lanes = new ArrayList<>();
            for (FroggetLane lane : other.lanes) {
                FroggetLane copy = new FroggetLane(lane.getRow(), lane.getDirection(), lane.getSpeed());
                for (FroggetObstacle ob : lane.getObstacles()) {
                    copy.getObstacles().add(new FroggetObstacle(ob.getCol(), ob.getLength(), ob.getSpriteId()));
                }
                s.lanes.add(copy);
            }
            return s;
        }
    }

    // -------------------------------------------------------------------------
    // Inner: FroggetLane
    // -------------------------------------------------------------------------
    public static final class FroggetLane {
        private final int row;
        private final int direction;
        private final int speed;
        private final List<FroggetObstacle> obstacles;

        public FroggetLane(final int row, final int direction, final int speed) {
            this.row = row;
            this.direction = direction;
            this.speed = speed;
            this.obstacles = new ArrayList<>();
        }

        public int getRow() { return row; }
        public int getDirection() { return direction; }
        public int getSpeed() { return speed; }
        public List<FroggetObstacle> getObstacles() { return obstacles; }
    }

    // -------------------------------------------------------------------------
    // Inner: FroggetObstacle
    // -------------------------------------------------------------------------
    public static final class FroggetObstacle {
        private int col;
        private final int length;
        private final int spriteId;

        public FroggetObstacle(final int col, final int length, final int spriteId) {
            this.col = col;
            this.length = length;
            this.spriteId = spriteId;
        }

        public int getCol() { return col; }
        public void setCol(final int col) { this.col = col; }
        public int getLength() { return length; }
        public int getSpriteId() { return spriteId; }
    }

    // -------------------------------------------------------------------------
    // Snapshot for external consumers (e.g. web UI)
    // -------------------------------------------------------------------------
    public static final class FroggetSnapshot {
        private final int frogRow;
        private final int frogCol;
        private final int lives;
        private final int score;
        private final int level;
        private final boolean gameOver;
        private final boolean levelComplete;
        private final List<LaneSnapshot> lanes;

        public FroggetSnapshot(
            final int frogRow,
            final int frogCol,
            final int lives,
            final int score,
            final int level,
            final boolean gameOver,
            final boolean levelComplete,
            final List<LaneSnapshot> lanes
        ) {
            this.frogRow = frogRow;
            this.frogCol = frogCol;
            this.lives = lives;
            this.score = score;
            this.level = level;
            this.gameOver = gameOver;
            this.levelComplete = levelComplete;
            this.lanes = lanes == null ? Collections.emptyList() : new ArrayList<>(lanes);
        }

        public int getFrogRow() { return frogRow; }
        public int getFrogCol() { return frogCol; }
        public int getLives() { return lives; }
        public int getScore() { return score; }
        public int getLevel() { return level; }
        public boolean isGameOver() { return gameOver; }
        public boolean isLevelComplete() { return levelComplete; }
        public List<LaneSnapshot> getLanes() { return lanes; }
    }

    public static final class LaneSnapshot {
        private final int row;
        private final int direction;
        private final List<ObstacleSnapshot> obstacles;

        public LaneSnapshot(final int row, final int direction, final List<ObstacleSnapshot> obstacles) {
            this.row = row;
            this.direction = direction;
            this.obstacles = obstacles == null ? Collections.emptyList() : new ArrayList<>(obstacles);
        }

        public int getRow() { return row; }
