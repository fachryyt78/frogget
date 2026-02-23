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
