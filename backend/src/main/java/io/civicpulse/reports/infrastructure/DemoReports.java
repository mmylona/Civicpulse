package io.civicpulse.reports.infrastructure;

import io.civicpulse.identity.TeamSummary;
import io.civicpulse.identity.UserDirectory;
import io.civicpulse.identity.UserSummary;
import io.civicpulse.reports.domain.ReportStatus;
import io.civicpulse.reports.domain.Severity;
import io.civicpulse.shared.Ids;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Fills an empty demo database with made-up reports around real places in Bern.
// Same seed every time, so the demo looks the same on every machine.
@Component
@Profile("demo")
@Order(2)
class DemoReports implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoReports.class);

    private static final String RESIDENTS = "residents@demo.local";
    private static final int BATCH_SIZE = 1000;

    private record Place(String name, double latitude, double longitude) {
    }

    private static final List<Place> PLACES = List.of(
            new Place("Bahnhofplatz", 46.9488, 7.4398),
            new Place("Bärenplatz", 46.9475, 7.4436),
            new Place("Zytglogge", 46.9479, 7.4478),
            new Place("Kornhausplatz", 46.9488, 7.4478),
            new Place("Bärengraben", 46.9481, 7.4593),
            new Place("Rosengarten", 46.9530, 7.4605),
            new Place("Helvetiaplatz", 46.9420, 7.4483),
            new Place("Thunplatz", 46.9387, 7.4600),
            new Place("Burgernziel", 46.9395, 7.4740),
            new Place("Breitenrainplatz", 46.9605, 7.4585),
            new Place("Viktoriaplatz", 46.9545, 7.4515),
            new Place("Lorraine", 46.9560, 7.4450),
            new Place("Wankdorfplatz", 46.9640, 7.4620),
            new Place("Bierhübeli", 46.9560, 7.4300),
            new Place("Länggassstrasse", 46.9555, 7.4320),
            new Place("Loryplatz", 46.9435, 7.4245),
            new Place("Eigerplatz", 46.9395, 7.4290),
            new Place("Weissenbühl", 46.9355, 7.4340),
            new Place("Monbijou", 46.9420, 7.4380),
            new Place("Europaplatz", 46.9430, 7.4040),
            new Place("Bümpliz Zentrum", 46.9450, 7.3880),
            new Place("Bethlehem", 46.9500, 7.3780),
            new Place("Brünnen", 46.9420, 7.3730),
            new Place("Tscharnergut", 46.9440, 7.3970),
            new Place("Felsenau", 46.9690, 7.4370));

    private static final Map<String, List<String>> TITLES = Map.ofEntries(
            Map.entry("POTHOLE", List.of("Pothole in the road", "Deep pothole on the bike lane", "Holes after winter")),
            Map.entry("ROAD_DAMAGE", List.of("Cracked asphalt", "Loose paving stones", "Kerb is broken")),
            Map.entry("STREET_LIGHT", List.of("Street light is out", "Street light flickers", "Lamp on all day")),
            Map.entry("TRAFFIC_SIGNAL", List.of("Traffic light not working", "Pedestrian light too short")),
            Map.entry("ILLEGAL_DUMPING", List.of("Furniture left on the pavement", "Bags of rubbish dumped")),
            Map.entry("OVERFLOWING_BIN", List.of("Bin is overflowing", "Recycling point full")),
            Map.entry("GRAFFITI", List.of("Graffiti on the wall", "Tags on the bus stop")),
            Map.entry("WATER_LEAK", List.of("Water coming out of the street", "Leaking hydrant")),
            Map.entry("BLOCKED_DRAIN", List.of("Drain blocked, water on the road", "Leaves blocking the gully")),
            Map.entry("PARK_DAMAGE", List.of("Broken bench", "Playground equipment damaged")),
            Map.entry("ACCESSIBILITY", List.of("Ramp blocked by a sign", "Lowered kerb missing")),
            Map.entry("OTHER", List.of("Something else", "Abandoned bicycle")));

    private static final Map<String, String> TEAMS = Map.ofEntries(
            Map.entry("POTHOLE", "Roads and Infrastructure"),
            Map.entry("ROAD_DAMAGE", "Roads and Infrastructure"),
            Map.entry("STREET_LIGHT", "Street Lighting"),
            Map.entry("TRAFFIC_SIGNAL", "Traffic"),
            Map.entry("ILLEGAL_DUMPING", "Waste Management"),
            Map.entry("OVERFLOWING_BIN", "Waste Management"),
            Map.entry("GRAFFITI", "Waste Management"),
            Map.entry("WATER_LEAK", "Water and Drainage"),
            Map.entry("BLOCKED_DRAIN", "Water and Drainage"),
            Map.entry("PARK_DAMAGE", "Parks and Public Space"),
            Map.entry("ACCESSIBILITY", "Parks and Public Space"),
            Map.entry("OTHER", "Parks and Public Space"));

    // weights out of 100
    private static final List<Map.Entry<ReportStatus, Integer>> STATUSES = List.of(
            Map.entry(ReportStatus.REPORTED, 30),
            Map.entry(ReportStatus.TRIAGED, 20),
            Map.entry(ReportStatus.ASSIGNED, 15),
            Map.entry(ReportStatus.IN_PROGRESS, 10),
            Map.entry(ReportStatus.WAITING_FOR_INFORMATION, 5),
            Map.entry(ReportStatus.RESOLVED, 10),
            Map.entry(ReportStatus.CLOSED, 10));

    private static final String INSERT_REPORT = """
            INSERT INTO reports (id, reference, reporter_id, category_code, title, description, stated_severity,
                                 validated_severity, latitude, longitude, address, status, resume_status, team_id,
                                 district_code, created_at, updated_at, resolved_at, closed_at)
            VALUES (?, 'CP-' || nextval('report_number_seq'), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    (SELECT code FROM districts WHERE ST_Covers(boundary, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                     ORDER BY code LIMIT 1),
                    ?, ?, ?, ?)
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO report_events (id, report_id, type, visibility, actor_id, actor_kind, payload, occurred_at)
            VALUES (?, ?, 'SUBMITTED', 'PUBLIC', ?, 'REPORTER', '{}'::jsonb, ?)
            """;

    private final UserDirectory directory;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final int count;

    DemoReports(UserDirectory directory, JdbcTemplate jdbcTemplate, JdbcClient jdbc, Clock clock,
            @Value("${civicpulse.demo.reports:300}") int count) {
        this.directory = directory;
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = jdbc;
        this.clock = clock;
        this.count = count;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UUID reporter = directory.findUserByEmail(RESIDENTS).map(UserSummary::id).orElse(null);
        if (reporter == null || count <= 0) {
            return;
        }
        long existing = jdbc.sql("SELECT count(*) FROM reports WHERE reporter_id = :id")
                .param("id", reporter)
                .query(Long.class)
                .single();
        if (existing > 0) {
            return;
        }
        Map<String, UUID> teams = new HashMap<>();
        for (TeamSummary team : directory.teams()) {
            teams.put(team.name(), team.id());
        }

        Random random = new Random(42);
        Instant now = clock.instant();
        List<String> categories = TITLES.keySet().stream().sorted().toList();
        List<Object[]> reports = new ArrayList<>();
        List<Object[]> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Place place = PLACES.get(random.nextInt(PLACES.size()));
            // about 250 m around the place
            double latitude = place.latitude() + random.nextGaussian() * 0.0023;
            double longitude = place.longitude() + random.nextGaussian() * 0.0033;
            String category = categories.get(random.nextInt(categories.size()));
            List<String> titles = TITLES.get(category);
            Severity severity = Severity.values()[Math.min(3, (int) Math.abs(random.nextGaussian() * 1.4))];
            UUID team = teams.get(TEAMS.get(category));
            ReportStatus status = pickStatus(random);
            if (team == null && needsTeam(status)) {
                status = ReportStatus.TRIAGED;
            }

            // resolved ones within the reopen window, so they are not closed right away
            Instant created = now.minus(Duration.ofMinutes(random.nextInt(120 * 24 * 60)));
            Instant resolved = null;
            Instant closed = null;
            if (status == ReportStatus.RESOLVED) {
                created = now.minus(Duration.ofMinutes(60 + random.nextInt(30 * 24 * 60)));
                long open = Duration.between(created, now).toMinutes();
                resolved = now.minus(Duration.ofMinutes(random.nextLong(Math.min(open, 12 * 24 * 60))));
            } else if (status == ReportStatus.CLOSED) {
                resolved = created.plus(Duration.between(created, now).dividedBy(2));
                closed = resolved.plus(Duration.ofDays(14)).isBefore(now) ? resolved.plus(Duration.ofDays(14)) : now;
            }
            Instant updated = closed != null ? closed : resolved != null ? resolved : created;

            UUID id = Ids.newId();
            reports.add(new Object[] {
                    id, reporter, category,
                    titles.get(random.nextInt(titles.size())),
                    "Seen near " + place.name() + ".",
                    severity.name(),
                    status == ReportStatus.REPORTED ? null : severity.name(),
                    latitude, longitude, place.name(), status.name(),
                    status == ReportStatus.WAITING_FOR_INFORMATION ? ReportStatus.TRIAGED.name() : null,
                    needsTeam(status) ? team : null,
                    longitude, latitude,
                    utc(created), utc(updated), utc(resolved), utc(closed)});
            events.add(new Object[] {Ids.newId(), id, reporter, utc(created)});
            if (reports.size() == BATCH_SIZE) {
                flush(reports, events);
            }
        }
        flush(reports, events);
        log.info("Created {} demo reports", count);
    }

    private void flush(List<Object[]> reports, List<Object[]> events) {
        if (!reports.isEmpty()) {
            jdbcTemplate.batchUpdate(INSERT_REPORT, reports);
            jdbcTemplate.batchUpdate(INSERT_EVENT, events);
            reports.clear();
            events.clear();
        }
    }

    // a resolved report can be reopened, which puts it back to its team
    private static boolean needsTeam(ReportStatus status) {
        return status == ReportStatus.ASSIGNED || status == ReportStatus.IN_PROGRESS
                || status == ReportStatus.RESOLVED || status == ReportStatus.CLOSED;
    }

    private static ReportStatus pickStatus(Random random) {
        int roll = random.nextInt(100);
        for (Map.Entry<ReportStatus, Integer> entry : STATUSES) {
            roll -= entry.getValue();
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return ReportStatus.REPORTED;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
