package com.tino.backend.credit.application.model;

import com.tino.backend.credit.domain.model.CreditAccount;
import com.tino.backend.credit.domain.model.CreditLedgerEntry;

public record CreditOperationResult(CreditLedgerEntry entry, CreditAccount account, boolean replayed) {}
