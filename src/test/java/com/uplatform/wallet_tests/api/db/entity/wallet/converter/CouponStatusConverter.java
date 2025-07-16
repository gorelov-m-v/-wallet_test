package com.uplatform.wallet_tests.api.db.entity.wallet.converter;

import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CouponStatusConverter implements AttributeConverter<CouponStatus, Byte> {
    @Override
    public Byte convertToDatabaseColumn(CouponStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDbValueAsByte();
    }

    @Override
    public CouponStatus convertToEntityAttribute(Byte dbData) {
        if (dbData == null) {
            return null;
        }
        return CouponStatus.findByDbValue(dbData.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Unknown database value for CouponStatus: " + dbData));
    }
}