package com.xiaozhi.utils;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 固件文件名约定：
 * firmware_v{version}_{timestamp}_{hash12}.bin
 */
public final class FirmwareUtils {

    private static final Pattern STORED_VERSION_PATTERN = Pattern.compile(
            "^firmware_v([A-Za-z0-9][A-Za-z0-9.-]{0,31})_.*\\.bin$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED_VERSION_PATTERN = Pattern.compile(
            "(?:^|[_-])(?:v|version[_-]?)([0-9][A-Za-z0-9.-]{0,31})(?:[_-]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HASH_PATTERN = Pattern.compile(
            "_([a-f0-9]{12})\\.bin$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VALID_VERSION_PATTERN = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9.-]{0,31}$");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private FirmwareUtils() {
    }

    public static String normalizeVersion(String version) {
        if (!StringUtils.hasText(version)) {
            return null;
        }

        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }

        if (!VALID_VERSION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("版本号只能包含字母、数字、点和连字符，长度不超过32位");
        }

        return normalized;
    }

    public static Optional<String> resolveVersion(String inputVersion, String fileName) {
        if (StringUtils.hasText(inputVersion)) {
            return Optional.of(normalizeVersion(inputVersion));
        }

        return extractVersion(fileName);
    }

    public static Optional<String> extractVersion(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return Optional.empty();
        }

        String safeName = fileName.trim();
        Matcher storedMatcher = STORED_VERSION_PATTERN.matcher(safeName);
        if (storedMatcher.matches()) {
            return Optional.of(storedMatcher.group(1));
        }

        String baseName = safeName.replaceFirst("(?i)\\.bin$", "");
        Matcher namedMatcher = NAMED_VERSION_PATTERN.matcher(baseName);
        if (namedMatcher.find()) {
            return Optional.of(normalizeVersion(namedMatcher.group(1)));
        }

        return Optional.empty();
    }

    public static String buildStorageFileName(String version, String sha256) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String hashPart = sha256.substring(0, Math.min(12, sha256.length())).toLowerCase(Locale.ROOT);
        return "firmware_v" + normalizeVersion(version) + "_" + timestamp + "_" + hashPart + ".bin";
    }

    public static Optional<String> extractHash(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return Optional.empty();
        }

        Matcher matcher = HASH_PATTERN.matcher(fileName.trim());
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toLowerCase(Locale.ROOT));
        }

        return Optional.empty();
    }
}
