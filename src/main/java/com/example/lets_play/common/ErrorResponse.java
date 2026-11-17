package com.example.lets_play.common;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {}