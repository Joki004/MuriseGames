import type { Area } from "react-easy-crop";
import type { PictureSlot } from "@/modules/Picture/picture.type";

export async function cropImage(
  source: string,
  area: Area,
  slot: PictureSlot,
): Promise<Blob> {
  const image = await new Promise<HTMLImageElement>((resolve, reject) => {
    const element = new Image();
    element.onload = () => resolve(element);
    element.onerror = () => reject(new Error("Could not read this image."));
    element.src = source;
  });

  if (
    area.width <= 0 ||
    area.height <= 0 ||
    area.x < 0 ||
    area.y < 0 ||
    area.x + area.width > image.naturalWidth + 1 ||
    area.y + area.height > image.naturalHeight + 1
  ) {
    throw new Error("Choose a valid crop.");
  }

  const canvas = document.createElement("canvas");
  canvas.width = slot === "EVENT_COVER" ? 1600 : 512;
  canvas.height = slot === "EVENT_COVER" ? 900 : 512;

  const context = canvas.getContext("2d");

  if (!context) {
    throw new Error("Image cropping is unavailable in this browser.");
  }

  context.fillStyle = "#0b1020";
  context.fillRect(0, 0, canvas.width, canvas.height);

  context.drawImage(
    image,
    area.x,
    area.y,
    area.width,
    area.height,
    0,
    0,
    canvas.width,
    canvas.height,
  );

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error("Could not prepare the cropped image."));
        } else if (blob.size > 5 * 1024 * 1024) {
          reject(new Error("The cropped image is too large."));
        } else {
          resolve(blob);
        }
      },
      "image/jpeg",
      0.9,
    );
  });
}