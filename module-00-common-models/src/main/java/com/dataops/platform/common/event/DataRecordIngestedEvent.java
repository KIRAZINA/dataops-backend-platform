package com.dataops.platform.common.event;

import com.dataops.platform.common.model.DataRecord;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class DataRecordIngestedEvent extends ApplicationEvent {
    private final DataRecord record;

    public DataRecordIngestedEvent(Object source, DataRecord record) {
        super(source);
        this.record = record;
    }
}