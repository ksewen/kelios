package com.github.ksewen.kelios.catalog;

import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.platform.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Explicit SQL/read projection; not a generic repository or HTTP response model. */
@Repository
public class CatalogLabelReads {
    private final JdbcTemplate jdbc;
    public CatalogLabelReads(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String FIELDS = "l.id,l.name,l.visibility,l.tenant_id,t.name AS tenant_name,"
        + "l.created_by,l.created_by_display_name,l.created_at,l.updated_by,l.updated_at,l.deleted_at,l.deleted_by";
    private static final String ACTOR_FIELDS = ",ub.display_name AS updated_by_display_name";
    private static final String ACTOR_JOINS = " LEFT JOIN app_user ub ON ub.id=l.updated_by";

    private record Row(UUID id, String name, String visibility, UUID tenantId, String tenantName,
            UUID createdBy, Instant createdAt, UUID updatedBy, Instant updatedAt, Instant deletedAt, UUID deletedBy,
            String createdByDisplayName, String updatedByDisplayName) {
        static Row read(ResultSet r) throws SQLException {
            return new Row(r.getObject("id", UUID.class), r.getString("name"), r.getString("visibility"),
                r.getObject("tenant_id", UUID.class), r.getString("tenant_name"),
                r.getObject("created_by", UUID.class), r.getTimestamp("created_at").toInstant(),
                r.getObject("updated_by", UUID.class), r.getTimestamp("updated_at").toInstant(),
                r.getTimestamp("deleted_at") == null ? null : r.getTimestamp("deleted_at").toInstant(),
                r.getObject("deleted_by", UUID.class),r.getString("created_by_display_name"),r.getString("updated_by_display_name"));
        }
        CatalogLabel model() {
            return new CatalogLabel(id, name, CatalogLabel.Visibility.valueOf(visibility),
                tenantId == null ? null : new CatalogLabel.Tenant(tenantId, tenantName),
                new CatalogLabel.Metadata(createdBy, createdAt, updatedBy, updatedAt,
                    createdByDisplayName,updatedByDisplayName), deletedAt, deletedBy);
        }
    }
    CatalogLabel detail(CatalogLabel.Kind kind, UUID id, UUID onlyTenant) {
        var args = new ArrayList<Object>(); args.add(id);
        String scope = "";
        if (onlyTenant != null) { scope = " AND (l.tenant_id IS NULL OR l.tenant_id=?)"; args.add(onlyTenant); }
        var rows = jdbc.query("SELECT " + FIELDS + ACTOR_FIELDS + " FROM " + kind.table()
            + " l LEFT JOIN tenant t ON t.id=l.tenant_id" + ACTOR_JOINS + " WHERE l.id=?" + scope,
            (r, n) -> Row.read(r).model(), args.toArray());
        if (rows.isEmpty()) throw BusinessFailure.missing();
        // Retained/deleted detail remains readable in authorized scope; it is not a repair command.
        return rows.getFirst();
    }
    Page<CatalogLabel> list(CatalogLabel.Kind kind, UUID effectiveTenant, CatalogLabelQuery q) {
        var args = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE l.deleted_at IS NULL");
        // The root shared-catalog list is the authorized unscoped view for a GLOBAL principal.
        if (effectiveTenant != null) { where.append(" AND (l.tenant_id IS NULL OR l.tenant_id=?)"); args.add(effectiveTenant); }
        if (q.visibility() != null) { where.append(" AND l.visibility=?"); args.add(q.visibility().name()); }
        if (q.search() != null) {
            where.append(" AND l.name ILIKE ? ESCAPE '\\'");
            args.add("%" + q.search().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        String field = q.sort() == null ? "l.created_at" : switch (q.sort()) {
            case NAME -> "l.name"; case VISIBILITY -> "l.visibility";
            case TENANT_NAME -> "CASE WHEN l.tenant_id IS NULL THEN 'Global' ELSE t.name END";
        };
        String direction = q.sort() == null ? "DESC" : q.direction().name();
        long count = jdbc.queryForObject("SELECT count(*) FROM " + kind.table() + " l" + where, Long.class, args.toArray());
        args.add(q.size()); args.add((long) q.page() * q.size());
        // Retain the existing SQL UUID tie order for predictable page boundaries, not as a Domain rule or selectable field.
        var items = jdbc.query("SELECT " + FIELDS + ACTOR_FIELDS + " FROM " + kind.table() + " l LEFT JOIN tenant t ON t.id=l.tenant_id"
            + ACTOR_JOINS
            + where + " ORDER BY " + field + " " + direction + ",l.id " + direction + " LIMIT ? OFFSET ?",
            (r, n) -> Row.read(r).model(), args.toArray());
        return new Page<>(items, q.page(), q.size(), count);
    }
}
