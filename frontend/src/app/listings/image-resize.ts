const MAX_SIDE = 1600;
const JPEG_QUALITY = 0.86;
const MAX_SOURCE_BYTES = 30 * 1024 * 1024;

export class ImagePreparationError extends Error {}

/**
 * Decodes any image format the browser supports (including HEIC on Safari), applies EXIF orientation,
 * and returns a JPEG no larger than 1600 px on the long edge. The server re-encodes it again.
 */
export async function prepareImage(file: File): Promise<Blob> {
  if (!file.type.startsWith('image/') && file.type !== '') {
    throw new ImagePreparationError('To nie jest plik graficzny.');
  }
  if (file.size > MAX_SOURCE_BYTES) {
    throw new ImagePreparationError('Plik jest zbyt duży. Wybierz zdjęcie mniejsze niż 30 MB.');
  }

  let bitmap: ImageBitmap;
  try {
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    throw new ImagePreparationError('Nie udało się odczytać tego zdjęcia. Spróbuj zapisać je jako JPEG.');
  }

  try {
    const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) {
      throw new ImagePreparationError('Twoja przeglądarka nie może przygotować zdjęcia.');
    }
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY));
    if (!blob) {
      throw new ImagePreparationError('Nie udało się przygotować zdjęcia.');
    }
    return blob;
  } finally {
    bitmap.close();
  }
}
