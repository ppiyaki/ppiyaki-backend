
    create table care_relations (
        caregiver_id bigint,
        created_at datetime(6),
        deleted_at datetime(6),
        id bigint not null auto_increment,
        senior_id bigint,
        updated_at datetime(6),
        primary key (id)
    ) engine=InnoDB;

    create table invite_codes (
        created_at datetime(6),
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        senior_id bigint not null,
        used_at datetime(6),
        code_hash varchar(255) not null,
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
        is_proxy bit not null,
        target_date date not null,
        confirmed_by_user_id bigint not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        schedule_id bigint not null,
        senior_id bigint not null,
        taken_at datetime(6),
        ai_status varchar(255),
        photo_url varchar(255),
        status enum ('MISSED','PENDING','TAKEN') not null,
        primary key (id)
    ) engine=InnoDB;

    alter table medication_logs
       add constraint uk_medication_logs_schedule_target_date unique (schedule_id, target_date);

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
        start_date date,
        created_at datetime(6),
        id bigint not null auto_increment,
        medicine_id bigint,
        days_of_week varchar(255),
        dosage varchar(255),
        meal_slot varchar(16) not null,
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

    create table notification_settings (
        dur_warning_enabled bit not null,
        family_safety_enabled bit not null,
        family_safety_threshold_hours integer not null,
        medication_complete_enabled bit not null,
        medication_delay_enabled bit not null,
        medication_delay_threshold_minutes integer not null,
        caregiver_id bigint not null,
        created_at datetime(6),
        id bigint not null auto_increment,
        senior_id bigint not null,
        updated_at datetime(6),
        primary key (id)
    ) engine=InnoDB;

    alter table notification_settings
       add constraint uk_caregiver_senior unique (caregiver_id, senior_id);

    create table notifications (
        target_date date,
        created_at datetime(6),
        id bigint not null auto_increment,
        read_at datetime(6),
        schedule_id bigint,
        senior_id bigint,
        updated_at datetime(6),
        user_id bigint not null,
        meal_slot varchar(16),
        category varchar(32) not null,
        title varchar(255) not null,
        body TEXT not null,
        payload JSON,
        primary key (id)
    ) engine=InnoDB;

    create index idx_notifications_user_created on notifications (user_id, created_at);

    alter table notifications
       add constraint uk_notifications_dedup unique (user_id, category, senior_id, target_date, meal_slot, schedule_id);

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
        last_taken_date date,
        point bigint not null,
        streak int not null default 0,
        updated_at datetime(6),
        highest_stage enum ('BABY','CRACKED_EGG','EGG','EMPEROR','GUARDIAN','HEALTHY') not null default 'EGG',
        primary key (id)
    ) engine=InnoDB;

    create table pill_identifications (
        synced_at datetime(6) not null,
        bizrno varchar(32),
        change_date varchar(32),
        class_no varchar(16),
        color_class1 varchar(32),
        color_class2 varchar(32),
        drug_shape varchar(32),
        edi_code varchar(255),
        etc_otc_name varchar(32),
        leng_long varchar(32),
        leng_short varchar(32),
        line_back varchar(32),
        line_front varchar(32),
        mark_code_back varchar(64),
        mark_code_front varchar(64),
        print_back varchar(64),
        print_front varchar(64),
        thick varchar(32),
        class_name varchar(128),
        item_seq varchar(20) not null,
        item_image varchar(512),
        item_name varchar(255) not null,
        entp_name varchar(255),
        chart TEXT,
        primary key (item_seq)
    ) engine=InnoDB;

    create index idx_pill_print_front on pill_identifications (print_front);
    create index idx_pill_shape_color on pill_identifications (drug_shape, color_class1);
    create index idx_pill_color_shape_line on pill_identifications (color_class1, drug_shape, line_front);
    create index idx_pill_item_name on pill_identifications (item_name);

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
        breakfast_time time(6),
        lunch_time time(6),
        dinner_time time(6),
        created_at datetime(6),
        id bigint not null auto_increment,
        last_active_at datetime(6),
        pet bigint,
        updated_at datetime(6),
        login_id varchar(255),
        nickname varchar(255),
        password varchar(255),
        auth_provider enum ('INVITE_ONLY','KAKAO','LOCAL') not null,
        care_mode enum ('AUTONOMOUS','MANAGED') not null,
        gender enum ('FEMALE','MALE','OTHER','UNKNOWN'),
        role enum ('CAREGIVER','SENIOR'),
        primary key (id)
    ) engine=InnoDB;

    create index idx_invite_codes_codehash_used on invite_codes (code_hash, used_at);
    create index idx_invite_codes_senior_used on invite_codes (senior_id, used_at);
    create index idx_invite_codes_expires on invite_codes (expires_at);

    alter table device_tokens
       add constraint UK8se1i37nto56x9252rmrit8ib unique (token);

    alter table oauth_identities 
       add constraint uk_oauth_identities_provider_user unique (provider, provider_user_id);

    alter table reports 
       add constraint uk_reports_senior_period unique (senior_id, period_type, period_start);

    alter table users
       add constraint UKi3xs7wmfu2i3jt079uuetycit unique (login_id);
