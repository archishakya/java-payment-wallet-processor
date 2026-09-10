package com.archi.paymentprocessor.controller;

import com.archi.paymentprocessor.dto.TransactionRequest;
import com.archi.paymentprocessor.dto.TransactionResponse;
import com.archi.paymentprocessor.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/process")
    public ResponseEntity<TransactionResponse> process(@RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.process(request);
        return ResponseEntity.ok(response);
    }
}
