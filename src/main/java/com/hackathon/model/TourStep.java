package com.hackathon.model;

/**
 * A single step of a code tour.
 *
 * Fields endLine and symbolName are optional and may be null in older JSONs.
 * - endLine: inclusive 1-based end line for multi-line selections (e.g., full method/class).
 * - symbolName: qualified or simple name of the selected symbol for better identification.
 */
public record TourStep(
        String filePath,
        int lineNum,
        String codeSnippet,
        String authorNote,
        String aiExplanation,
        Integer endLine,
        String symbolName
) {}
