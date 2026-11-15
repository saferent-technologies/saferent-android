package com.saferenttechnologies.android.data.property

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCropTest {
    private val aspect = CapturedPhotoWriter.PHOTO_ASPECT

    @Test fun `portrait shot keeps the full width, 16 by 9 band from the middle`() =
        assertEquals(3000 to 1688, CapturedPhotoWriter.cropSize(3000, 4000, aspect))

    @Test fun `landscape 4 by 3 shot loses some height`() =
        assertEquals(4000 to 2250, CapturedPhotoWriter.cropSize(4000, 3000, aspect))

    @Test fun `already 16 by 9 stays as it is`() =
        assertEquals(1920 to 1080, CapturedPhotoWriter.cropSize(1920, 1080, aspect))
}
