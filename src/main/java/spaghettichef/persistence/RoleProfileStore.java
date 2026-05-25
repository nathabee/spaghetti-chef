package spaghettichef.persistence;

import spaghettichef.OperationMessages;
import spaghettichef.security.LocalRole;
import spaghettichef.security.Permission;
import spaghettichef.security.RoleProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RoleProfileStore {
    private static final Pattern JSON_STRING_PATTERN = Pattern.compile("\"([^\"]+)\"");

    public Map<LocalRole, RoleProfile> loadAll() {
        ensureBuiltInProfiles();

        String sql = """
                SELECT
                    role_name,
                    permissions_json,
                    built_in
                FROM role_profiles
                ORDER BY role_name;
                """;

        Map<LocalRole, RoleProfile> profiles = new java.util.EnumMap<>(LocalRole.class);

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                LocalRole role = LocalRole.valueOf(resultSet.getString("role_name"));
                profiles.put(role, new RoleProfile(
                        role,
                        role.displayName(),
                        parsePermissions(resultSet.getString("permissions_json")),
                        resultSet.getInt("built_in") == 1));
            }

            return Map.copyOf(profiles);
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_ROLE_PROFILES, exception);
        }
    }

    public void saveAll(Map<LocalRole, RoleProfile> profiles) {
        if (profiles == null) {
            throw new IllegalArgumentException(OperationMessages.ROLE_PROFILES_MUST_NOT_BE_NULL);
        }

        for (RoleProfile profile : profiles.values()) {
            save(profile);
        }
    }

    public RoleProfile save(RoleProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("roleProfile"));
        }

        String sql = """
                INSERT INTO role_profiles (
                    role_name,
                    permissions_json,
                    built_in,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT(role_name) DO UPDATE SET
                    permissions_json = excluded.permissions_json,
                    built_in = role_profiles.built_in,
                    updated_at = CURRENT_TIMESTAMP;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.role().name());
            statement.setString(2, permissionsJson(profile.permissions()));
            statement.setInt(3, profile.builtIn() ? 1 : 0);
            statement.executeUpdate();
            return profile;
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_SAVE_ROLE_PROFILES, exception);
        }
    }

    public void ensureBuiltInProfiles() {
        for (RoleProfile profile : RoleProfile.builtInProfiles().values()) {
            if (!exists(profile.role())) {
                save(profile);
            }
        }
    }

    public static String permissionsJson(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "[]";
        }

        return permissions.stream()
                .map(permission -> "\"" + permission.name() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    public static Set<Permission> parsePermissions(String json) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        if (json == null || json.isBlank()) {
            return permissions;
        }

        Matcher matcher = JSON_STRING_PATTERN.matcher(json);
        while (matcher.find()) {
            permissions.add(Permission.valueOf(matcher.group(1)));
        }

        return permissions;
    }

    private boolean exists(LocalRole role) {
        String sql = """
                SELECT 1
                FROM role_profiles
                WHERE role_name = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, role.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_LOAD_ROLE_PROFILES, exception);
        }
    }
}
