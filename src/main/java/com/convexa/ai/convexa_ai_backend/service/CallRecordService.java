package com.convexa.ai.convexa_ai_backend.service;

import com.convexa.ai.convexa_ai_backend.entity.CallRecord;
import com.convexa.ai.convexa_ai_backend.repository.CallRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CallRecordService {

    @Autowired
    private CallRecordRepository callRecordRepository;

    // Save Call Record
    public CallRecord saveCallRecord(CallRecord callRecord) {
        return callRecordRepository.save(callRecord);
    }

    // Get All Records
    public List<CallRecord> getAllCallRecords() {
        return callRecordRepository.findAll();
    }

    // Get Record By ID
    public CallRecord getCallRecordById(Long id) {
        return callRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Call Record Not Found"));
    }

    // Delete Record
    public void deleteCallRecord(Long id) {
        callRecordRepository.deleteById(id);
    }

    public List<CallRecord> getCallsByUserId(
            Long userId
    ) {
        return callRecordRepository.findByUserId(
                userId
        );
    }

    public List<CallRecord> getCallsByCompanyId(Long companyId) {
        return callRecordRepository.findByUserCompanyId(companyId);
    }
}