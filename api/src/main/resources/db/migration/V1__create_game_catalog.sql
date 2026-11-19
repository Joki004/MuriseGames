-- One row per game, not one table per game. No unapproved scoring rules are seeded.
create table murise.games (
    code varchar(80) primary key,
    name varchar(160) not null,
    rules_status varchar(40) not null default 'PENDING_REVIEW',
    constraint games_rules_status check (rules_status in ('PENDING_REVIEW', 'APPROVED'))
);

-- The application can read the catalogue. Write grants arrive with write features.
grant select on murise.games to murise_app;

insert into murise.games (code, name) values
    ('cup', 'Cup'),
    ('sensory', 'Sensory'),
    ('alibi', 'Alibi'),
    ('social', 'Social'),
    ('poison', 'Poison'),
    ('grid', 'Grid'),
    ('group-chat', 'Group Chat'),
    ('rehearsal', 'Rehearsal'),
    ('premiere', 'Premiere');
