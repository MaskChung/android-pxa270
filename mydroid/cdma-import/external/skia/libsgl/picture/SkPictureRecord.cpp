#include "SkPictureRecord.h"
#include "SkTSearch.h"

#define MIN_WRITER_SIZE 16384
#define HEAP_BLOCK_SIZE 4096

SkPictureRecord::SkPictureRecord() :
        fHeap(HEAP_BLOCK_SIZE), fWriter(MIN_WRITER_SIZE) {
    fBitmapIndex = fMatrixIndex = fPaintIndex = fPathIndex = fRegionIndex = 1;
#ifdef SK_DEBUG_SIZE
    fPointBytes = fRectBytes = fTextBytes = 0;
    fPointWrites = fRectWrites = fTextWrites = 0;
#endif

    fRestoreOffsetStack.setReserve(32);
    fRestoreOffsetStack.push(0);
}

SkPictureRecord::~SkPictureRecord() {
    reset();
}

///////////////////////////////////////////////////////////////////////////////

int SkPictureRecord::save(SaveFlags flags) {
    addDraw(SAVE);
    addInt(flags);
    
    fRestoreOffsetStack.push(0);
    
    validate();
    return this->INHERITED::save(flags);
}

int SkPictureRecord::saveLayer(const SkRect* bounds, const SkPaint* paint,
                               SaveFlags flags) {
    addDraw(SAVE_LAYER);
    addRectPtr(bounds);
    addPaintPtr(paint);
    addInt(flags);

    fRestoreOffsetStack.push(0);

    validate();
    return this->INHERITED::saveLayer(bounds, paint, flags);
}

void SkPictureRecord::restore() {
    
    // patch up the clip offsets
    {
        uint32_t restoreOffset = (uint32_t)fWriter.size();
        uint32_t offset = fRestoreOffsetStack.top();
        while (offset) {
            uint32_t* peek = fWriter.peek32(offset);
            offset = *peek;
            *peek = restoreOffset;
        }
        fRestoreOffsetStack.pop();
    }
    
    addDraw(RESTORE);
    validate();
    return this->INHERITED::restore();
}

bool SkPictureRecord::translate(SkScalar dx, SkScalar dy) {
    addDraw(TRANSLATE);
    addScalar(dx);
    addScalar(dy);
    validate();
    return this->INHERITED::translate(dx, dy);
}

bool SkPictureRecord::scale(SkScalar sx, SkScalar sy) {
    addDraw(SCALE); 
    addScalar(sx);
    addScalar(sy);
    validate();
    return this->INHERITED::scale(sx, sy);
}

bool SkPictureRecord::rotate(SkScalar degrees) {
    addDraw(ROTATE); 
    addScalar(degrees); 
    validate();
    return this->INHERITED::rotate(degrees);
}

bool SkPictureRecord::skew(SkScalar sx, SkScalar sy) {
    addDraw(SKEW); 
    addScalar(sx);
    addScalar(sy);
    validate();
    return this->INHERITED::skew(sx, sy);
}

bool SkPictureRecord::concat(const SkMatrix& matrix) {
    validate();
    addDraw(CONCAT);
    addMatrix(matrix);
    validate();
    return this->INHERITED::concat(matrix);
}

bool SkPictureRecord::clipRect(const SkRect& rect, SkRegion::Op op) {
    addDraw(CLIP_RECT);
    addRect(rect);
    addInt(op);
    
    size_t offset = fWriter.size();
    addInt(fRestoreOffsetStack.top());
    fRestoreOffsetStack.top() = offset;

    validate();
    return this->INHERITED::clipRect(rect, op);
}

bool SkPictureRecord::clipPath(const SkPath& path, SkRegion::Op op) {
    addDraw(CLIP_PATH);
    addPath(path);
    addInt(op);
    
    size_t offset = fWriter.size();
    addInt(fRestoreOffsetStack.top());
    fRestoreOffsetStack.top() = offset;
    
    validate();
    return this->INHERITED::clipPath(path, op);
}

bool SkPictureRecord::clipRegion(const SkRegion& region, SkRegion::Op op) {
    addDraw(CLIP_REGION); 
    addRegion(region);
    addInt(op);
    
    size_t offset = fWriter.size();
    addInt(fRestoreOffsetStack.top());
    fRestoreOffsetStack.top() = offset;
    
    validate();
    return this->INHERITED::clipRegion(region, op);
}

void SkPictureRecord::drawPaint(const SkPaint& paint) {
    addDraw(DRAW_PAINT);
    addPaint(paint);
    validate();
}

void SkPictureRecord::drawPoints(PointMode mode, size_t count, const SkPoint pts[],
                        const SkPaint& paint) {
    addDraw(DRAW_POINTS);
    addPaint(paint);
    addInt(mode);
    addInt(count);
    fWriter.writeMul4(pts, count * sizeof(SkPoint));
    validate();
}

// return true if geometry drawn with this paint will just be filled
// i.e. its bounding rect will not be larger than the original geometry
static bool is_simple_fill(const SkPaint& paint) {
    return  paint.getStyle() == SkPaint::kFill_Style &&
    paint.getPathEffect() == NULL &&
    paint.getMaskFilter() == NULL &&
    paint.getRasterizer() == NULL;
}

void SkPictureRecord::drawRect(const SkRect& rect, const SkPaint& paint) {
    addDraw(is_simple_fill(paint) ? DRAW_RECT_SIMPLE : DRAW_RECT_GENERAL);
    addPaint(paint);
    addRect(rect);
    validate();
}

void SkPictureRecord::drawPath(const SkPath& path, const SkPaint& paint) {
    addDraw(DRAW_PATH);
    addPaint(paint);
    addPath(path);
    validate();
}

void SkPictureRecord::drawBitmap(const SkBitmap& bitmap, SkScalar left, SkScalar top,
                        const SkPaint* paint = NULL) {
    addDraw(DRAW_BITMAP);
    addPaintPtr(paint);
    addBitmap(bitmap);
    addScalar(left);
    addScalar(top);
    validate();
}

void SkPictureRecord::drawBitmapRect(const SkBitmap& bitmap, const SkIRect* src,
                            const SkRect& dst, const SkPaint* paint) {
    addDraw(DRAW_BITMAP_RECT);
    addPaintPtr(paint);
    addBitmap(bitmap);
    addIRectPtr(src);  // may be null
    addRect(dst);
    validate();
}

void SkPictureRecord::drawBitmapMatrix(const SkBitmap& bitmap, const SkMatrix& matrix,
                              const SkPaint* paint) {
    addDraw(DRAW_BITMAP_MATRIX);
    addPaintPtr(paint);
    addBitmap(bitmap);
    addMatrix(matrix);
    validate();
}

void SkPictureRecord::drawSprite(const SkBitmap& bitmap, int left, int top,
                        const SkPaint* paint = NULL) {
    addDraw(DRAW_SPRITE);
    addPaintPtr(paint);
    addBitmap(bitmap);
    addInt(left);
    addInt(top);
    validate();
}

void SkPictureRecord::addFontMetricsTopBottom(const SkPaint& paint,
                                              SkScalar baselineY) {
    SkPaint::FontMetrics metrics;
    paint.getFontMetrics(&metrics);
    addScalar(metrics.fTop + baselineY);
    addScalar(metrics.fBottom + baselineY);
}

void SkPictureRecord::drawText(const void* text, size_t byteLength, SkScalar x, 
                      SkScalar y, const SkPaint& paint) {
    addDraw(DRAW_TEXT);
    addPaint(paint);
    addText(text, byteLength);
    addScalar(x);
    addScalar(y);
    addFontMetricsTopBottom(paint, y);
    validate();
}

void SkPictureRecord::drawPosText(const void* text, size_t byteLength, 
                         const SkPoint pos[], const SkPaint& paint) {
    size_t points = paint.countText(text, byteLength);
    if (0 == points)
        return;

    bool canUseDrawH = true;
    // check if the caller really should have used drawPosTextH()
    {
        const SkScalar firstY = pos[0].fY;
        for (size_t index = 1; index < points; index++) {
            if (pos[index].fY != firstY) {
                canUseDrawH = false;
                break;
            }
        }
    }
    
    addDraw(canUseDrawH ? DRAW_POS_TEXT_H : DRAW_POS_TEXT);
    addPaint(paint);
    addText(text, byteLength);
    addInt(points);

#ifdef SK_DEBUG_SIZE
    size_t start = fWriter.size();
#endif
    if (canUseDrawH) {
        addFontMetricsTopBottom(paint, pos[0].fY);
        addScalar(pos[0].fY);
        SkScalar* xptr = (SkScalar*)fWriter.reserve(points * sizeof(SkScalar));
        for (size_t index = 0; index < points; index++) 
            *xptr++ = pos[index].fX;
    }
    else {
        fWriter.writeMul4(pos, points * sizeof(SkPoint));
    }
#ifdef SK_DEBUG_SIZE
    fPointBytes += fWriter.size() - start;
    fPointWrites += points;
#endif
    validate();
}

void SkPictureRecord::drawPosTextH(const void* text, size_t byteLength,
                          const SkScalar xpos[], SkScalar constY,
                          const SkPaint& paint) {
    size_t points = paint.countText(text, byteLength);
    if (0 == points)
        return;
    
    addDraw(DRAW_POS_TEXT_H);
    addPaint(paint);
    addText(text, byteLength);
    addInt(points);
    
#ifdef SK_DEBUG_SIZE
    size_t start = fWriter.size();
#endif
    addFontMetricsTopBottom(paint, constY);
    addScalar(constY);
    fWriter.writeMul4(xpos, points * sizeof(SkScalar));
#ifdef SK_DEBUG_SIZE
    fPointBytes += fWriter.size() - start;
    fPointWrites += points;
#endif
    validate();
}

void SkPictureRecord::drawTextOnPath(const void* text, size_t byteLength, 
                            const SkPath& path, const SkMatrix* matrix, 
                            const SkPaint& paint) {
    addDraw(DRAW_TEXT_ON_PATH);
    addPaint(paint);
    addText(text, byteLength);
    addPath(path);
    addMatrixPtr(matrix);
    validate();
}

void SkPictureRecord::drawPicture(SkPicture& picture) {
    addDraw(DRAW_PICTURE);
    addPicture(picture);
    validate();
}

void SkPictureRecord::drawVertices(VertexMode vmode, int vertexCount,
                          const SkPoint vertices[], const SkPoint texs[],
                          const SkColor colors[], SkXfermode*,
                          const uint16_t indices[], int indexCount,
                          const SkPaint& paint) {
    uint32_t flags = 0;
    if (texs) {
        flags |= DRAW_VERTICES_HAS_TEXS;
    }
    if (colors) {
        flags |= DRAW_VERTICES_HAS_COLORS;
    }
    if (indexCount > 0) {
        flags |= DRAW_VERTICES_HAS_INDICES;
    }

    addDraw(DRAW_VERTICES);
    addPaint(paint);
    addInt(flags);
    addInt(vmode);
    addInt(vertexCount);
    addPoints(vertices, vertexCount);
    if (flags & DRAW_VERTICES_HAS_TEXS) {
        addPoints(texs, vertexCount);
    }
    if (flags & DRAW_VERTICES_HAS_COLORS) {
        fWriter.writeMul4(colors, vertexCount * sizeof(SkColor));
    }
    if (flags & DRAW_VERTICES_HAS_INDICES) {
        addInt(indexCount);
        fWriter.writePad(indices, indexCount * sizeof(uint16_t));
    }
}

///////////////////////////////////////////////////////////////////////////////
    
void SkPictureRecord::reset() {
    fBitmaps.reset();
    fMatrices.reset();
    fPaints.reset();
    fPaths.reset();
    fPictureRefs.unrefAll();
    fRegions.reset();
    fWriter.reset();
    fHeap.reset();
    
    fRestoreOffsetStack.setCount(1);
    fRestoreOffsetStack.top() = 0;
    
    fRCRecorder.reset();
    fTFRecorder.reset();
}

void SkPictureRecord::addBitmap(const SkBitmap& bitmap) {
    addInt(find(fBitmaps, bitmap));
}

void SkPictureRecord::addMatrix(const SkMatrix& matrix) {
    addMatrixPtr(&matrix);
}

void SkPictureRecord::addMatrixPtr(const SkMatrix* matrix) {
    addInt(find(fMatrices, matrix));
}

void SkPictureRecord::addPaint(const SkPaint& paint) {
    addPaintPtr(&paint);
}

void SkPictureRecord::addPaintPtr(const SkPaint* paint) {
    addInt(find(fPaints, paint));
}

void SkPictureRecord::addPath(const SkPath& path) {
    addInt(find(fPaths, path));
}

void SkPictureRecord::addPicture(SkPicture& picture) {
    int index = fPictureRefs.find(&picture);
    if (index < 0) {    // not found
        index = fPictureRefs.count();
        *fPictureRefs.append() = &picture;
        picture.ref();
    }
    // follow the convention of recording a 1-based index
    addInt(index + 1);
}

void SkPictureRecord::addPoint(const SkPoint& point) {
#ifdef SK_DEBUG_SIZE
    size_t start = fWriter.size();
#endif
    fWriter.writePoint(point);
#ifdef SK_DEBUG_SIZE
    fPointBytes += fWriter.size() - start;
    fPointWrites++;
#endif
}
    
void SkPictureRecord::addPoints(const SkPoint pts[], int count) {
    fWriter.writeMul4(pts, count * sizeof(SkPoint));
#ifdef SK_DEBUG_SIZE
    fPointBytes += count * sizeof(SkPoint);
    fPointWrites++;
#endif
}

void SkPictureRecord::addRect(const SkRect& rect) {
#ifdef SK_DEBUG_SIZE
    size_t start = fWriter.size();
#endif
    fWriter.writeRect(rect);
#ifdef SK_DEBUG_SIZE
    fRectBytes += fWriter.size() - start;
    fRectWrites++;
#endif
}

void SkPictureRecord::addRectPtr(const SkRect* rect) {
    if (fWriter.writeBool(rect != NULL)) {
        fWriter.writeRect(*rect);
    }
}

void SkPictureRecord::addIRectPtr(const SkIRect* rect) {
    if (fWriter.writeBool(rect != NULL)) {
        *(SkIRect*)fWriter.reserve(sizeof(SkIRect)) = *rect;
    }
}

void SkPictureRecord::addRegion(const SkRegion& region) {
    addInt(find(fRegions, region));
}

void SkPictureRecord::addText(const void* text, size_t byteLength) {
#ifdef SK_DEBUG_SIZE
    size_t start = fWriter.size();
#endif
    addInt(byteLength);
    fWriter.writePad(text, byteLength);
#ifdef SK_DEBUG_SIZE
    fTextBytes += fWriter.size() - start;
    fTextWrites++;
#endif
}

///////////////////////////////////////////////////////////////////////////////

int SkPictureRecord::find(SkTDArray<const SkFlatBitmap* >& bitmaps, const SkBitmap& bitmap) {
    SkFlatBitmap* flat = SkFlatBitmap::Flatten(&fHeap, bitmap, fBitmapIndex,
                                               &fRCRecorder);
    int index = SkTSearch<SkFlatData>((const SkFlatData**) bitmaps.begin(), 
        bitmaps.count(), (SkFlatData*) flat, sizeof(flat), &SkFlatData::Compare);
    if (index >= 0) {
//        SkBitmap bitmap;
//        flat->unflatten(&bitmap); // balance ref count
        return bitmaps[index]->index();
    }
    index = ~index;
    *bitmaps.insert(index) = flat;
    return fBitmapIndex++;
}

int SkPictureRecord::find(SkTDArray<const SkFlatMatrix* >& matrices, const SkMatrix* matrix) {
    if (matrix == NULL)
        return 0;
    SkFlatMatrix* flat = SkFlatMatrix::Flatten(&fHeap, *matrix, fMatrixIndex);
    int index = SkTSearch<SkFlatData>((const SkFlatData**) matrices.begin(), 
        matrices.count(), (SkFlatData*) flat, sizeof(flat), &SkFlatData::Compare);
    if (index >= 0)
        return matrices[index]->index();
    index = ~index;
    *matrices.insert(index) = flat;
    return fMatrixIndex++;
}

int SkPictureRecord::find(SkTDArray<const SkFlatPaint* >& paints, const SkPaint* paint) {
    if (paint == NULL) {
        return 0;
    }
    
    SkFlatPaint* flat = SkFlatPaint::Flatten(&fHeap, *paint, fPaintIndex,
                                             &fRCRecorder, &fTFRecorder);
    int index = SkTSearch<SkFlatData>((const SkFlatData**) paints.begin(), 
        paints.count(), (SkFlatData*) flat, sizeof(flat), &SkFlatData::Compare);
    if (index >= 0) {
        return paints[index]->index();
    }

    index = ~index;
    *paints.insert(index) = flat;
    return fPaintIndex++;
}

int SkPictureRecord::find(SkTDArray<const SkFlatPath* >& paths, const SkPath& path) {
    SkFlatPath* flat = SkFlatPath::Flatten(&fHeap, path, fPathIndex);
    int index = SkTSearch<SkFlatData>((const SkFlatData**) paths.begin(), 
        paths.count(), (SkFlatData*) flat, sizeof(flat), &SkFlatData::Compare);
    if (index >= 0)
        return paths[index]->index();
    index = ~index;
    *paths.insert(index) = flat;
    return fPathIndex++;
}

int SkPictureRecord::find(SkTDArray<const SkFlatRegion* >& regions, const SkRegion& region) {
    SkFlatRegion* flat = SkFlatRegion::Flatten(&fHeap, region, fRegionIndex);
    int index = SkTSearch<SkFlatData>((const SkFlatData**) regions.begin(), 
        regions.count(), (SkFlatData*) flat, sizeof(flat), &SkFlatData::Compare);
    if (index >= 0)
        return regions[index]->index();
    index = ~index;
    *regions.insert(index) = flat;
    return fRegionIndex++;
}

#ifdef SK_DEBUG_DUMP
void SkPictureRecord::dumpMatrices() {
    int count = fMatrices.count();
    SkMatrix defaultMatrix;
    defaultMatrix.reset();
    for (int index = 0; index < count; index++) {
        const SkFlatMatrix* flatMatrix = fMatrices[index];
        flatMatrix->dump();
    }
}

void SkPictureRecord::dumpPaints() {
    int count = fPaints.count();
    for (int index = 0; index < count; index++) 
        fPaints[index]->dump();
}
#endif

#ifdef SK_DEBUG_SIZE
size_t SkPictureRecord::size() const {
    size_t result = 0;
    size_t sizeData;
    bitmaps(&sizeData);
    result += sizeData;
    matrices(&sizeData);
    result += sizeData;
    paints(&sizeData);
    result += sizeData;
    paths(&sizeData);
    result += sizeData;
    pictures(&sizeData);
    result += sizeData;
    regions(&sizeData);
    result += sizeData;
    result += streamlen();
    return result;
}

int SkPictureRecord::bitmaps(size_t* size) const {
    size_t result = 0;
    int count = fBitmaps.count();
    for (int index = 0; index < count; index++) 
        result += sizeof(fBitmaps[index]) + fBitmaps[index]->size();
    *size = result;
    return count;
}

int SkPictureRecord::matrices(size_t* size) const {
    int count = fMatrices.count();
    *size = sizeof(fMatrices[0]) * count;
    return count;
}

int SkPictureRecord::paints(size_t* size) const {
    size_t result = 0;
    int count = fPaints.count();
    for (int index = 0; index < count; index++) 
        result += sizeof(fPaints[index]) + fPaints[index]->size();
    *size = result;
    return count;
}

int SkPictureRecord::paths(size_t* size) const {
    size_t result = 0;
    int count = fPaths.count();
    for (int index = 0; index < count; index++) 
        result += sizeof(fPaths[index]) + fPaths[index]->size();
    *size = result;
    return count;
}

int SkPictureRecord::regions(size_t* size) const {
    size_t result = 0;
    int count = fRegions.count();
    for (int index = 0; index < count; index++) 
        result += sizeof(fRegions[index]) + fRegions[index]->size();
    *size = result;
    return count;
}

size_t SkPictureRecord::streamlen() const {
    return fWriter.size();
}
#endif

#ifdef SK_DEBUG_VALIDATE
void SkPictureRecord::validate() const {
    validateBitmaps();
    validateMatrices();
    validatePaints();
    validatePaths();
    validatePictures();
    validateRegions();
}

void SkPictureRecord::validateBitmaps() const {
    int count = fBitmaps.count();
    SkASSERT((unsigned) count < 0x1000);
    for (int index = 0; index < count; index++) {
        const SkFlatBitmap* bitPtr = fBitmaps[index];
        SkASSERT(bitPtr);
        bitPtr->validate();
    }
}

void SkPictureRecord::validateMatrices() const {
    int count = fMatrices.count();
    SkASSERT((unsigned) count < 0x1000);
    for (int index = 0; index < count; index++) {
        const SkFlatMatrix* matrix = fMatrices[index];
        SkASSERT(matrix);
        matrix->validate(); 
    }
}

void SkPictureRecord::validatePaints() const {
    int count = fPaints.count();
    SkASSERT((unsigned) count < 0x1000);
    for (int index = 0; index < count; index++) {
        const SkFlatPaint* paint = fPaints[index];
        SkASSERT(paint);
//            paint->validate();
    }
}

void SkPictureRecord::validatePaths() const {
    int count = fPaths.count();
    SkASSERT((unsigned) count < 0x1000);
    for (int index = 0; index < count; index++) {
        const SkFlatPath* path = fPaths[index];
        SkASSERT(path);
        path->validate();
    }
}

void SkPictureRecord::validateRegions() const {
    int count = fRegions.count();
    SkASSERT((unsigned) count < 0x1000);
    for (int index = 0; index < count; index++) {
        const SkFlatRegion* region = fRegions[index];
        SkASSERT(region);
        region->validate();
    }
}
#endif

