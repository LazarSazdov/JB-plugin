package com.hackathon.model;

import java.util.List;

/**
 * A tour made of ordered steps with a title.
 */
public record Tour(
        String title,
        List<TourStep> steps
) {}
