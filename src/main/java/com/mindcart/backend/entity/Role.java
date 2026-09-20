package com.mindcart.backend.entity;

public enum Role {
    OWNER(3), WRITE(2), READ(1);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(Role other) {
        return this.rank >= other.rank;
    }
}
