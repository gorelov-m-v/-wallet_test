package com.uplatform.wallet_tests.api.db.entity.wallet.converter;

import com.uplatform.wallet_tests.api.db.entity.wallet.enums.CouponType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CouponTypeConverter implements AttributeConverter<CouponType, Byte> {

    @Override
    public Byte convertToDatabaseColumn(CouponType attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDbValueAsByte();
    }

    @Override
    public CouponType convertToEntityAttribute(Byte dbData) {
        if (dbData == null) {
            return null;
        }
        return CouponType.findByDbValue(dbData.intValue())
                .orElseThrow(() -> new IllegalArgumentException("Unknown database value for CouponType: " + dbData));
    }
}