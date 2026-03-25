import { useState, useEffect, useRef, useCallback } from "react";
import type { CSSProperties } from "react";
import Cropper from "react-easy-crop";
import type { Area } from "react-easy-crop";
import { ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/Button";
import { uploadProfileImage } from "@/features/auth/api";
import { cropImage } from "@/features/auth/utils/cropImage";
import type { MeResponse } from "@/shared/api/types";

interface ProfileImageCropDialogProps {
  imageSrc: string;
  onClose: () => void;
  onSuccess: (response: MeResponse) => void;
}

const sliderStyle: CSSProperties = {
  WebkitAppearance: "none",
  appearance: "none",
  height: "6px",
  background: "var(--slider-track, #d1d5db)",
  outline: "none",
  cursor: "pointer",
};

export function ProfileImageCropDialog({ imageSrc, onClose, onSuccess }: ProfileImageCropDialogProps) {
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedAreaPixels, setCroppedAreaPixels] = useState<Area | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const dialogRef = useRef<HTMLDivElement>(null);

  const onCropComplete = useCallback((_: Area, pixels: Area) => {
    setCroppedAreaPixels(pixels);
  }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !isSubmitting) {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isSubmitting, onClose]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (!isSubmitting && dialogRef.current && !dialogRef.current.contains(e.target as Node)) {
        onClose();
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isSubmitting, onClose]);

  const handleSubmit = async () => {
    if (!croppedAreaPixels) {
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const blob = await cropImage(imageSrc, croppedAreaPixels);
      const response = await uploadProfileImage(blob);
      onSuccess(response);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("이미지 업로드에 실패했습니다");
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div
        ref={dialogRef}
        className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-w-sm"
      >
        <div className="px-6 py-5 space-y-4">
          <h2 className="text-xl font-black">프로필 이미지 편집</h2>

          <div className="relative w-full aspect-square border-4 border-black dark:border-white overflow-hidden bg-gray-100 dark:bg-gray-800">
            <Cropper
              image={imageSrc}
              crop={crop}
              zoom={zoom}
              aspect={1}
              cropShape="rect"
              onCropChange={setCrop}
              onZoomChange={setZoom}
              onCropComplete={onCropComplete}
              style={{
                containerStyle: { position: "absolute", inset: 0 },
              }}
            />
          </div>

          <div className="flex items-center gap-3">
            <span className="text-sm font-bold shrink-0">줌</span>
            <input
              type="range"
              min={1}
              max={10}
              step={0.1}
              value={zoom}
              onChange={(e) => setZoom(Number(e.target.value))}
              className="w-full"
              style={sliderStyle}
              disabled={isSubmitting}
            />
          </div>

          {error && <p className="text-sm font-medium text-red-500">{error}</p>}
        </div>

        <div className="flex gap-3 px-6 py-4 border-t-4 border-black dark:border-white">
          <Button variant="secondary" size="sm" className="flex-1" onClick={onClose} disabled={isSubmitting}>
            취소
          </Button>
          <Button size="sm" className="flex-1" onClick={handleSubmit} isLoading={isSubmitting} disabled={isSubmitting}>
            저장
          </Button>
        </div>
      </div>
    </div>
  );
}
