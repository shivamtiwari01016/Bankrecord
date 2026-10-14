package com.bankrecon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BankRecon — Banking Transaction & Reconciliation System.
 * <p>
 * Educational application demonstrating Java/Spring Boot backend development,
 * relational database design, transaction processing, and reconciliation workflows.
 * <p>
 * This application uses synthetic data only. It does not integrate with real banks,
 * payment gateways, or real customer accounts. It does NOT implement production-grade
 * banking security, regulatory compliance, or real payment processing.
 */
@SpringBootApplication
public class BankReconApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankReconApplication.class, args);
    }
}
