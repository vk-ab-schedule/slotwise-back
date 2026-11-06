-- D-16: the model now uses five-minute slots. Existing values (multiples of 15) remain valid.
alter table slotwise.session_rule
    drop constraint session_rule_room_change_buffer_minutes_check;
alter table slotwise.session_rule
    add constraint session_rule_room_change_buffer_minutes_check
        check (room_change_buffer_minutes >= 0 and room_change_buffer_minutes % 5 = 0);
