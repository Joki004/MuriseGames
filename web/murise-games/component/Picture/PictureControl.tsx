"use client";

import Image from "next/image";
import Cropper, { type Area } from "react-easy-crop";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
} from "react";
import { Alert, Button, Dialog, Input } from "@/component/ui";
import { useAuth } from "@/component/Auth/AuthProvider";
import { PictureController } from "@/modules/Picture/picture.controller";
import {
  PICTURE_EMOJIS,
  type PictureResponse,
  type PictureSlot,
} from "@/modules/Picture/picture.type";
import { cropImage } from "./crop-image";

interface PictureControlProps {
  slot: PictureSlot;
  targetId: string;
  label: string;
  editable?: boolean;
  color?: string;
}

const CHANGE_EVENT = "murise-picture-changed";

function pictureKey(slot: PictureSlot, targetId: string) {
  return `${slot}:${targetId}`;
}

function announceChange(slot: PictureSlot, targetId: string) {
  window.dispatchEvent(
    new CustomEvent(CHANGE_EVENT, {
      detail: pictureKey(slot, targetId),
    }),
  );
}

export function PictureControl(props: PictureControlProps) {
  const { account } = useAuth();

  return (
    <PictureControlContent
      key={`${account?.id ?? "signed-out"}:${props.slot}:${props.targetId}`}
      {...props}
    />
  );
}

function PictureControlContent({
  slot,
  targetId,
  label,
  editable = false,
  color,
}: PictureControlProps) {
  const [picture, setPicture] = useState<PictureResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    let sequence = 0;

    async function load() {
      const request = ++sequence;

      try {
        const response = await PictureController.get(slot, targetId);

        if (active && request === sequence) {
          setPicture(response);
          setError(null);
        }
      } catch (err: unknown) {
        if (active && request === sequence) {
          setPicture(null);
          setError(
            err instanceof Error ? err.message : "Could not load picture.",
          );
        }
      }
    }

    function changed(event: Event) {
      if (
        (event as CustomEvent<string>).detail === pictureKey(slot, targetId)
      ) {
        void load();
      }
    }

    function refresh() {
      void load();
    }

    void load();

    // Signed links last five minutes.
    const interval = window.setInterval(refresh, 3 * 60 * 1000);

    window.addEventListener(CHANGE_EVENT, changed);
    window.addEventListener("focus", refresh);

    return () => {
      active = false;
      window.clearInterval(interval);
      window.removeEventListener(CHANGE_EVENT, changed);
      window.removeEventListener("focus", refresh);
    };
  }, [slot, targetId, revision]);

  const canEdit = editable && picture?.canEdit === true;

  return (
    <div
      className={
        slot === "EVENT_COVER"
          ? "w-full min-w-0 space-y-3"
          : "flex shrink-0 flex-col items-start gap-2"
      }
    >
      <PictureVisual
        key={picture?.imageUrl ?? picture?.emoji ?? "fallback"}
        slot={slot}
        label={label}
        imageUrl={picture?.imageUrl ?? null}
        emoji={picture?.emoji ?? null}
        color={color}
      />

      {canEdit && (
        <Button
          variant="secondary"
          className="text-sm"
          onClick={() => setOpen(true)}
        >
          {slot === "EVENT_COVER" ? "Change cover" : "Change picture"}
        </Button>
      )}

      {editable && error && (
        <div className="max-w-sm space-y-2">
          <p role="alert" className="text-sm text-danger">{error}</p>
          <Button
            variant="secondary"
            onClick={() => setRevision((value) => value + 1)}
          >
            Retry
          </Button>
        </div>
      )}

      <Dialog
        open={open}
        onOpenChange={setOpen}
        title={slot === "EVENT_COVER" ? "Event cover" : "Choose a picture"}
        description={
          slot === "EVENT_COVER"
            ? "Upload a wide image or remove it to use the themed gradient."
            : "Choose an emoji or upload and crop a picture."
        }
        busy={busy}
      >
        {open && picture && (
          <PictureEditor
            slot={slot}
            targetId={targetId}
            label={label}
            current={picture}
            color={color}
            onBusyChange={setBusy}
            onSaved={() => {
              announceChange(slot, targetId);
              setOpen(false);
            }}
          />
        )}
      </Dialog>
    </div>
  );
}

function PictureVisual({
  slot,
  label,
  imageUrl,
  emoji,
  color,
}: {
  slot: PictureSlot;
  label: string;
  imageUrl: string | null;
  emoji: string | null;
  color?: string;
}) {
  const [failed, setFailed] = useState(false);
  const cover = slot === "EVENT_COVER";

  const initials = label
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => Array.from(part)[0] ?? "")
    .join("")
    .toUpperCase();

  const fallback = slot === "AVATAR"
    ? initials || "?"
    : slot === "TEAM_ICON"
      ? initials || "🎲"
      : "🎲";

  const gradients = [
    "linear-gradient(135deg, #151d32, #5b21b6 65%, #b45309)",
    "linear-gradient(135deg, #0b1020, #312e81 55%, #7c3aed)",
    "linear-gradient(135deg, #202b45, #581c87 60%, #92400e)",
  ];

  const themeHash = Array.from(label).reduce(
    (total, character) => total + (character.codePointAt(0) ?? 0),
    0,
  );

  return (
    <div
      role="img"
      aria-label={cover ? `Cover for ${label}` : `Picture for ${label}`}
      className={[
        "relative overflow-hidden border-2 border-border",
        cover
          ? "aspect-video w-full rounded-card"
          : "flex h-16 w-16 items-center justify-center bg-surface-raised text-2xl",
        !cover && slot === "AVATAR" ? "rounded-full" : "",
        !cover && slot !== "AVATAR" ? "rounded-card" : "",
      ].join(" ")}
      style={{
        borderColor: color,
        backgroundImage: cover
          ? gradients[themeHash % gradients.length]
          : undefined,
      }}
    >
      {imageUrl && !failed ? (
        <Image
          src={imageUrl}
          alt=""
          fill
          unoptimized
          sizes={cover ? "(max-width: 768px) 100vw, 1152px" : "64px"}
          className="object-cover"
          onError={() => setFailed(true)}
        />
      ) : !cover ? (
        <span aria-hidden="true" className="font-semibold text-foreground">
          {emoji || fallback}
        </span>
      ) : null}
    </div>
  );
}

interface PictureEditorProps {
  slot: PictureSlot;
  targetId: string;
  label: string;
  current: PictureResponse;
  color?: string;
  onBusyChange: (busy: boolean) => void;
  onSaved: () => void;
}

function PictureEditor({
  slot,
  targetId,
  label,
  current,
  color,
  onBusyChange,
  onSaved,
}: PictureEditorProps) {
  const cover = slot === "EVENT_COVER";

  const [mode, setMode] = useState<"emoji" | "upload">(
    cover || current.imageUrl ? "upload" : "emoji",
  );
  const [emoji, setEmoji] = useState(current.emoji ?? "");
  const [source, setSource] = useState<string | null>(null);
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [pixels, setPixels] = useState<Area | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const busy = useRef(false);
  const objectUrl = useRef<string | null>(null);

  useEffect(() => {
    return () => {
      if (objectUrl.current) {
        URL.revokeObjectURL(objectUrl.current);
      }
    };
  }, []);

  const onCropComplete = useCallback(
    (_area: Area, croppedPixels: Area) => {
      setPixels(croppedPixels);
    },
    [],
  );

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";

    if (!file) return;

    if (!["image/jpeg", "image/png"].includes(file.type)) {
      setError("Choose a JPEG or PNG image.");
      return;
    }

    if (file.size > 8 * 1024 * 1024) {
      setError("Choose an original image smaller than 8 MB.");
      return;
    }

    if (objectUrl.current) {
      URL.revokeObjectURL(objectUrl.current);
    }

    const url = URL.createObjectURL(file);
    objectUrl.current = url;

    setSource(url);
    setCrop({ x: 0, y: 0 });
    setZoom(1);
    setPixels(null);
    setError(null);
  }

  async function perform(operation: () => Promise<void>) {
    if (busy.current) return;

    busy.current = true;
    setSaving(true);
    onBusyChange(true);
    setError(null);

    try {
      await operation();
      onSaved();
    } catch (err: unknown) {
      setError(
        err instanceof Error ? err.message : "Could not save the picture.",
      );
    } finally {
      busy.current = false;
      setSaving(false);
      onBusyChange(false);
    }
  }

  function save() {
    void perform(async () => {
      if (mode === "emoji" && !cover) {
        if (!emoji) throw new Error("Choose an emoji.");
        await PictureController.chooseEmoji(slot, targetId, emoji);
        return;
      }

      if (!source || !pixels) {
        throw new Error("Choose an image and adjust its crop.");
      }

      const file = await cropImage(source, pixels, slot);
      await PictureController.upload(slot, targetId, file);
    });
  }

  const canSave = mode === "emoji"
    ? Boolean(emoji)
    : Boolean(source && pixels);

  return (
    <div className="space-y-5">
      <fieldset disabled={saving} className="space-y-5">
        {!cover && (
          <div
            role="group"
            aria-label="Picture type"
            className="grid grid-cols-2 gap-2"
          >
            <Button
              variant="secondary"
              aria-pressed={mode === "emoji"}
              onClick={() => setMode("emoji")}
              className={mode === "emoji" ? "border-highlight text-highlight" : ""}
            >
              Emoji
            </Button>

            <Button
              variant="secondary"
              aria-pressed={mode === "upload"}
              onClick={() => setMode("upload")}
              className={mode === "upload" ? "border-highlight text-highlight" : ""}
            >
              Upload
            </Button>
          </div>
        )}

        {mode === "emoji" && !cover ? (
          <>
            <div className="flex justify-center">
              <PictureVisual
                slot={slot}
                label={label}
                emoji={emoji || null}
                imageUrl={null}
                color={color}
              />
            </div>

            <div
              role="group"
              aria-label="Available emojis"
              className="grid grid-cols-4 gap-2"
            >
              {PICTURE_EMOJIS.map(([value, name]) => (
                <Button
                  key={value}
                  variant="secondary"
                  aria-label={name}
                  aria-pressed={emoji === value}
                  className={
                    emoji === value
                      ? "border-highlight bg-surface-raised text-2xl"
                      : "text-2xl"
                  }
                  onClick={() => setEmoji(value)}
                >
                  <span aria-hidden="true">{value}</span>
                </Button>
              ))}
            </div>
          </>
        ) : (
          <div className="space-y-4">
            <Input
              label={source ? "Replace selected image" : "Choose an image"}
              type="file"
              accept="image/jpeg,image/png"
              onChange={chooseFile}
              hint="JPEG or PNG, up to 8 MB before cropping."
            />

            {source ? (
              <>
                <div
                  className={[
                    "relative h-72 overflow-hidden rounded-control bg-background",
                    saving ? "pointer-events-none" : "",
                  ].join(" ")}
                >
                  <Cropper
                    image={source}
                    crop={crop}
                    zoom={zoom}
                    aspect={cover ? 16 / 9 : 1}
                    cropShape={slot === "AVATAR" ? "round" : "rect"}
                    onCropChange={setCrop}
                    onZoomChange={setZoom}
                    onCropComplete={onCropComplete}
                    keyboardStep={10}
                    zoomWithScroll={false}
                    onMediaLoaded={(size) => {
                      if (
                        size.naturalWidth > 6000 ||
                        size.naturalHeight > 6000 ||
                        size.naturalWidth * size.naturalHeight > 24_000_000
                      ) {
                        setSource(null);
                        setPixels(null);
                        setError(
                          "Choose an image up to 6000 pixels per side and 24 megapixels.",
                        );
                      }
                    }}
                    mediaProps={{
                      onError: () => {
                        setPixels(null);
                        setError("This image could not be opened.");
                      },
                    }}
                  />
                </div>

                <Input
                  label="Zoom"
                  type="range"
                  min={1}
                  max={3}
                  step={0.05}
                  value={zoom}
                  onChange={(event) => setZoom(Number(event.target.value))}
                />

                <p className="text-sm text-muted">
                  Drag the image to position it. You can also focus the crop
                  area and use the arrow keys.
                </p>
              </>
            ) : (
              <PictureVisual
                key={current.imageUrl ?? "current"}
                slot={slot}
                label={label}
                imageUrl={current.imageUrl}
                emoji={current.emoji}
                color={color}
              />
            )}
          </div>
        )}

        <div className="flex flex-col gap-3 sm:flex-row sm:justify-between">
          <Button
            variant="danger"
            disabled={!current.imageUrl && !current.emoji}
            onClick={() =>
              void perform(() =>
                PictureController.remove(slot, targetId),
              )
            }
          >
            Remove picture
          </Button>

          <Button
            disabled={!canSave}
            loading={saving}
            loadingText="Saving…"
            onClick={save}
          >
            Save picture
          </Button>
        </div>
      </fieldset>

      {error && <Alert tone="error">{error}</Alert>}
    </div>
  );
}