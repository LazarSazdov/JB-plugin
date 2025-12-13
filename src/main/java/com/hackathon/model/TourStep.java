package com.hackathon.model;

/**
 * A single step of a code tour.
 */
public record TourStep(
        String filePath,
        int lineNum,
        String codeSnippet,
        String authorNote,
        String aiExplanation
) {}
