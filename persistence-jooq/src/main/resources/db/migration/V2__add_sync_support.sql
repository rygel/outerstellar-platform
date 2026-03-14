alter table messages add column sync_id varchar(36);
update messages set sync_id = cast(random_uuid() as varchar(36)) where sync_id is null;
alter table messages alter column sync_id set not null;
create unique index ux_messages_sync_id on messages(sync_id);

alter table messages add column updated_at_epoch_ms bigint default 0 not null;
update messages
set updated_at_epoch_ms = datediff('MILLISECOND', timestamp '1970-01-01 00:00:00', created_at)
where updated_at_epoch_ms = 0;

alter table messages add column deleted boolean default false not null;
alter table messages add column dirty boolean default false not null;

create table sync_state (
    state_key varchar(64) primary key,
    state_value bigint not null
);
