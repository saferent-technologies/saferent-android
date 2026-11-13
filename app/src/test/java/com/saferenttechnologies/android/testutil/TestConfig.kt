package com.saferenttechnologies.android.testutil

/** The real "data" object returned by GET api/v1/app/config (HTTP 200, ETag "app-config-3"). */
object TestConfig {
    const val REAL_ETAG = "\"app-config-3\""

    val REAL_DATA_JSON = """
    {
      "configVersion": 3,
      "updatedAt": "2026-11-13T16:40:12.000Z",
      "app": { "name": "SafeRent", "packageName": "com.saferenttechnologies.android", "latestVersion": "1.0.0",
               "minimumSupportedVersion": "1.0.0", "forceUpdate": false,
               "updateMessage": "A new version of SafeRent is available. Please update to continue.", "playStoreUrl": "" },
      "maintenance": { "enabled": false, "title": "", "message": "", "startsAt": null, "expectedEndAt": null },
      "features": { "properties": true, "tenants": true, "landlords": true, "rentManagement": true, "rentPayments": true,
                    "rentReminders": true, "documents": true, "rentalAgreements": true, "electricityBills": true,
                    "propertyTaxDocuments": true, "otherDocuments": true, "notifications": true, "referrals": true,
                    "support": true, "chat": false },
      "auth": { "emailLogin": true, "phoneLogin": true, "emailOtp": true, "phoneOtp": true, "googleLogin": false,
                "otpExpiryMinutes": 5, "otpResendSeconds": 60, "otpMaxAttempts": 5 },
      "properties": {
        "enabledTypes": [
          { "key": "APARTMENT", "label": "Apartment", "category": "RESIDENTIAL", "structure": "MULTI_UNIT" },
          { "key": "INDIVIDUAL_HOUSE", "label": "Independent house", "category": "RESIDENTIAL", "structure": "SINGLE_UNIT" },
          { "key": "PG", "label": "PG", "category": "RESIDENTIAL", "structure": "ROOMS_BEDS" },
          { "key": "OFFICE_SPACE", "label": "Office space", "category": "COMMERCIAL", "structure": "MULTI_UNIT" }
        ],
        "maxImages": 40, "maxDocuments": 50, "maxImageSizeMB": 5, "allowedImageFormats": ["jpg", "jpeg", "png", "webp"] },
      "documents": { "agreement": true, "electricityBill": true, "propertyTax": true, "otherDocuments": true,
                     "customNameRequired": true, "maxFileSizeMB": 10, "allowedFormats": ["pdf", "jpg", "jpeg", "png"],
                     "notifyTenantOnUpload": true, "defaultVisibility": "PRIVATE" },
      "notifications": { "pushEnabled": true, "rentDue": true, "rentOverdue": true, "paymentSuccess": true,
                         "documentUploaded": true, "agreementUploaded": true, "propertyAssigned": true, "maintenance": true },
      "payments": { "enabled": true, "razorpay": true, "upi": true, "card": true, "netbanking": true, "currency": "INR",
                    "convenienceFee": { "type": "NONE", "value": 0, "maxAmount": null }, "minAmount": 10, "maxAmount": 100000 },
      "referrals": { "enabled": true, "minSuccessfulReferrals": 1,
                     "eligibilityRules": "Rewards are credited after the referred person completes verification and their first lease or listing.",
                     "programs": [ { "type": "TENANT_TO_TENANT", "rewardAmount": 50, "durationMonths": null },
                                   { "type": "LANDLORD_TO_TENANT", "rewardAmount": 50, "durationMonths": null },
                                   { "type": "LANDLORD_TO_LANDLORD", "rewardAmount": null, "durationMonths": 1 } ] },
      "support": { "email": "support@saferenttechnologies.com", "phone": "", "whatsapp": "", "helpCenterUrl": "", "faqEnabled": false },
      "splash": { "enabled": true, "title": "SafeRent", "description": "Verified homes. Honest renting.",
                  "logoUrl": "http://192.168.1.46:4000/api/v1/app/assets/0b6f2c1e-6d3a-4f7e-9a51-1c2d3e4f5a6b.png",
                  "lottieUrl": "http://192.168.1.46:4000/api/v1/app/assets/7c1d9e2a-3b4f-4a5e-8f6d-2e3f4a5b6c7d.json",
                  "minDurationMs": 1500 },
      "onboarding": { "enabled": true, "showOnce": true, "skippable": true, "contentId": "3f9a1c7b2e10",
        "screens": [
          { "id": "screen-a1b2c3", "title": "Verified owners", "description": "Every owner is verified with DigiLocker before listing.", "mediaType": "LOTTIE", "mediaUrl": "http://192.168.1.46:4000/api/v1/app/assets/1a2b3c4d-1111-4a5e-8f6d-2e3f4a5b6c7d.json" },
          { "id": "screen-d4e5f6", "title": "Every charge upfront", "description": "Rent, deposit and maintenance before you apply.", "mediaType": "IMAGE", "mediaUrl": "https://cdn.example.com/charges.webp" },
          { "id": "screen-g7h8i9", "title": "Your rental in one place", "description": "Lease, rent and receipts — all online.", "mediaType": "VIDEO", "mediaUrl": "http://192.168.1.46:4000/api/v1/app/assets/5e6f7a8b-2222-4a5e-8f6d-2e3f4a5b6c7d.mp4" } ] },
      "ui": { "primaryColor": "#F97316", "secondaryColor": "#111827", "accentColor": "#FB923C", "logoUrl": "", "darkMode": true,
              "announcementEnabled": false, "announcementTitle": "", "announcementMessage": "",
              "bannerEnabled": false, "bannerImageUrl": "", "bannerLinkUrl": "" },
      "legal": { "privacyPolicyUrl": "", "termsUrl": "", "refundPolicyUrl": "" }
    }
    """.trimIndent()
}
