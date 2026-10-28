-- Skema DB TCRApps (sudah dipakai saat init Otomatis oleh migrate.js)
create table if not exists trips (
    id          bigserial primary key,
    device_id   text not null default 'unknown',
    started_at  timestamptz not null default now(),
    finished_at timestamptz,
    distance_m  double precision
);

create table if not exists track_points (
    id          bigserial primary key,
    trip_id     bigint not null references trips(id) on delete cascade,
    ts          timestamptz not null default now(),
    lat         double precision not null,
    lon         double precision not null,
    speed_kmh   double precision,
    accuracy_m  double precision,
    heading     double precision
);

create index if not exists idx_track_points_trip on track_points (trip_id, ts);
create index if not exists idx_trips_started on trips (started_at desc);
