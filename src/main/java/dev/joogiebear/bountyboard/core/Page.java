package dev.joogiebear.bountyboard.core;

import java.util.List;

public record Page<T>(List<T> items, int page, int pages, int total) {}
