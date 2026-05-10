package com.lumebridge.db;

public record TaskRecord(int version, String responseHash, String resultJson) {}
