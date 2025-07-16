package com.uplatform.wallet_tests.api.db.entity.wallet.converter;

import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponCalcStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CouponCalcStatusConverter implements AttributeConverter<CouponCalcStatus, Byte> {

    @Override
    public Byte convertToDatabaseColumn(CouponCalcStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDbValueAsByte();
    }

    @Override
    public CouponCalcStatus convertToEntityAttribute(Byte dbData) {
        if (dbData == null) {
            return null;
        }
        return CouponCalcStatus.findByDbValue(dbData.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Unknown database value for CouponCalcStatus: " + dbData));
    }
}