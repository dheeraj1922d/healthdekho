package com.healthdekho.service;

import com.healthdekho.entity.InsuranceProduct;

/** One recommended product. fromAi = true when Bedrock chose it, false for the simple catalog ranking. */
record Suggestion(InsuranceProduct product, String reason, String suitability, boolean fromAi) {}
