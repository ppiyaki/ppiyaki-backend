
    create table care_relations (
        caregiver_id bigint,
        created_at datetime(6),
        deleted_at datetime(6),
        id bigint not null auto_increment,
        senior_id bigint,
        updated_at datetime(6),
        invite_code varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table device_tokens (
        is_active bit not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        last_seen_at datetime(6),
        updated_at datetime(6),
        user_id bigint not null,
        token varchar(255) not null,
        platform enum ('ANDROID','IOS','WEB') not null,
        primary key (id)
    ) engine=InnoDB;

    create table dur_checks (
        checked_at datetime(6) not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        medicine_id bigint not null,
        raw_response TEXT,
        warning_text TEXT,
        warning_level enum ('BLOCK','INFO','NONE','WARN'),
        primary key (id)
    ) engine=InnoDB;

    create table health_profiles (
        drinking_status bit,
        smoking_status bit,
        created_at datetime(6),
        id bigint not null auto_increment,
        senior_id bigint,
        allergies varchar(255),
        diet_habits varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table medication_logs (
        is_proxy bit,
        target_date date,
        confirmed_by_user_id bigint,
        created_at datetime(6),
        id bigint not null auto_increment,
        schedule_id bigint,
        senior_id bigint,
        taken_at datetime(6),
        ai_status varchar(255),
        photo_url varchar(255),
        status varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table medication_reminders (
        target_date date not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        schedule_id bigint not null,
        scheduled_at datetime(6) not null,
        senior_id bigint not null,
        sent_at datetime(6),
        updated_at datetime(6),
        error_message varchar(255),
        channel enum ('PUSH','TTS','VOICE') not null,
        delivery_status enum ('DELIVERED','FAILED','PENDING','SENT') not null,
        primary key (id)
    ) engine=InnoDB;

    create table medication_schedules (
        end_date date,
        scheduled_time time(6),
        start_date date,
        created_at datetime(6),
        id bigint not null auto_increment,
        medicine_id bigint,
        days_of_week varchar(255),
        dosage varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table medicines (
        remaining_amount integer,
        total_amount integer,
        created_at datetime(6),
        id bigint not null auto_increment,
        owner_id bigint not null,
        prescription_id bigint,
        dur_warning_text varchar(255),
        name varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table oauth_identities (
        created_at datetime(6),
        id bigint not null auto_increment,
        user_id bigint not null,
        provider_user_id varchar(255) not null,
        provider enum ('KAKAO') not null,
        primary key (id)
    ) engine=InnoDB;

    create table pets (
        created_at datetime(6),
        id bigint not null auto_increment,
        point bigint,
        updated_at datetime(6),
        primary key (id)
    ) engine=InnoDB;

    create table prescriptions (
        caregiver_id bigint,
        created_at datetime(6),
        id bigint not null auto_increment,
        senior_id bigint,
        extracted_text TEXT,
        ocr_image_url varchar(255),
        status varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table reports (
        adherence_rate decimal(5,2),
        period_end date not null,
        period_start date not null,
        total_missed integer not null,
        total_scheduled integer not null,
        total_taken integer not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        senior_id bigint not null,
        period_type enum ('DAILY','MONTHLY') not null,
        primary key (id)
    ) engine=InnoDB;

    create table users (
        dob date,
        created_at datetime(6),
        id bigint not null auto_increment,
        pet bigint,
        updated_at datetime(6),
        login_id varchar(255),
        nickname varchar(255),
        password varchar(255),
        care_mode enum ('AUTONOMOUS','MANAGED') not null,
        gender enum ('FEMALE','MALE','OTHER','UNKNOWN'),
        role enum ('CAREGIVER','SENIOR'),
        primary key (id)
    ) engine=InnoDB;

    alter table device_tokens 
       add constraint UK8se1i37nto56x9252rmrit8ib unique (token);

    alter table oauth_identities 
       add constraint uk_oauth_identities_provider_user unique (provider, provider_user_id);

    alter table reports 
       add constraint uk_reports_senior_period unique (senior_id, period_type, period_start);

    alter table users 
       add constraint UKi3xs7wmfu2i3jt079uuetycit unique (login_id);
