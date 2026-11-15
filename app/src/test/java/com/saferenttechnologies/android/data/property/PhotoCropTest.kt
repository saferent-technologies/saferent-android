package com.saferenttechnologies.android.data.property

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCropTest {
    private val aspect = CapturedPhotoWriter.PHOTO_ASPECT

    @Test fun `portrait shot keeps the full width, 4 by 3 band from the middle`() =
        assertEquals(3000 to 2250, CapturedPhotoWriter.cropSize(3000, 4000, aspect))

    @Test fun `landscape 4 by 3 shot stays as it is`() =
        assertEquals(4000 to 3000, CapturedPhotoWriter.cropSize(4000, 3000, aspect))

    @Test fun `16 by 9 shot loses some width`() =
        assertEquals(1440 to 1080, CapturedPhotoWriter.cropSize(1920, 1080, aspect))
}
