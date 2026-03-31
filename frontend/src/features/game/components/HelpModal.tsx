import { useEffect, useRef } from "react";

interface HelpModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const HELP_SHOWN_KEY = "help_modal_shown";

export function HelpModal({ isOpen, onClose }: HelpModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    const handleClickOutside = (e: MouseEvent) => {
      if (modalRef.current && !modalRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isOpen, onClose]);

  if (!isOpen) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      role="dialog"
      aria-modal="true"
      aria-labelledby="help-modal-title"
    >
      <div
        ref={modalRef}
        className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-w-lg max-h-[80vh] flex flex-col"
      >
        <div className="flex items-center justify-between border-b-4 border-black dark:border-white px-6 py-4 shrink-0">
          <h2 id="help-modal-title" className="text-xl font-black">
            게임 방법
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white px-3 py-1 font-black text-lg transition-all duration-100 shadow-brutal-sm hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]"
            aria-label="닫기"
          >
            ✕
          </button>
        </div>

        <div className="px-6 py-5 overflow-y-auto">
          <section className="pb-5">
            <h3 className="text-base font-black text-gray-700 dark:text-gray-300 mb-2">개요</h3>
            <p className="text-sm font-medium leading-relaxed">
              <strong className="font-black">가까워</strong>는 매일 출제되는 정답 문장을 AI 유사도를 기반으로 맞추는
              게임입니다.
              <br />
              정답에 가까운 <strong className="font-black">의미</strong>의 문장을 입력할수록 높은 유사도를 얻습니다.
            </p>
          </section>

          <section className="border-t-2 border-gray-200 dark:border-gray-800 pt-5 pb-5">
            <h3 className="text-base font-black text-gray-700 dark:text-gray-300 mb-2">AI 유사도란?</h3>
            <p className="text-sm font-medium leading-relaxed mb-3">
              이 게임의 유사도는 <strong className="font-black">문장의 의미적 유사성</strong>을 측정합니다.
              <br />
              글자가 겹치거나 초성이 비슷하다고 높은 점수가 나오지 않습니다.
              <br />
              AI가 문장의 <strong className="font-black">뜻과 맥락</strong>을 분석하여 정답과 얼마나 가까운 의미인지
              판단합니다.
            </p>
            <div className="border-2 border-black dark:border-white bg-gray-50 dark:bg-gray-800 p-3 space-y-2 text-sm">
              <p className="font-bold text-green-700 dark:text-green-400">높은 유사도 — 의미가 비슷한 경우</p>
              <p className="font-medium text-gray-600 dark:text-gray-400">
                정답: &quot;오늘 날씨가 좋다&quot; → &quot;화창한 하루다&quot; (비슷한 뜻)
              </p>
              <div className="border-t border-gray-300 dark:border-gray-600 my-1" />
              <p className="font-bold text-red-600 dark:text-red-400">낮은 유사도 — 의미가 다른 경우</p>
              <p className="font-medium text-gray-600 dark:text-gray-400">
                정답: &quot;오늘 날씨가 좋다&quot; → &quot;오늘 국수가 좋다&quot; (비슷하지만 뜻이 다름)
              </p>
            </div>
          </section>

          <section className="border-t-2 border-gray-200 dark:border-gray-800 pt-5 pb-5">
            <h3 className="text-base font-black text-gray-700 dark:text-gray-300 mb-3">플레이 방법</h3>
            <ol className="space-y-2.5 text-sm font-medium">
              <li className="flex gap-3">
                <span className="shrink-0 w-6 h-6 border-2 border-black dark:border-white bg-yellow-300 flex items-center justify-center text-xs font-black">
                  -
                </span>
                <span>
                  힌트 마스크로 정답의 <strong className="font-black">단어 수와 글자 수</strong>를 확인하세요.
                </span>
              </li>
              <li className="flex gap-3">
                <span className="shrink-0 w-6 h-6 border-2 border-black dark:border-white bg-yellow-300 flex items-center justify-center text-xs font-black">
                  -
                </span>
                <span>정답이라고 생각하는 문장을 입력하세요.</span>
              </li>
              <li className="flex gap-3">
                <span className="shrink-0 w-6 h-6 border-2 border-black dark:border-white bg-yellow-300 flex items-center justify-center text-xs font-black">
                  -
                </span>
                <span>
                  AI가 <strong className="font-black">유사도(0~100%)</strong>를 알려줍니다.
                </span>
              </li>
              <li className="flex gap-3">
                <span className="shrink-0 w-6 h-6 border-2 border-black dark:border-white bg-yellow-300 flex items-center justify-center text-xs font-black">
                  -
                </span>
                <span>
                  유사도 <strong className="font-black">95% 이상</strong>이면 정답! 차지합니다.
                </span>
              </li>
              <li className="flex gap-3">
                <span className="shrink-0 w-6 h-6 border-2 border-black dark:border-white bg-yellow-300 flex items-center justify-center text-xs font-black">
                  -
                </span>
                <span>적은 시도로 맞출수록 높은 순위를 차지합니다.</span>
              </li>
            </ol>
            <p className="mt-3 text-sm font-medium leading-relaxed border-l-4 border-yellow-400 pl-3 bg-yellow-50 dark:bg-yellow-900/20 py-2">
              - 정답을 맞힌 이후에도 계속 추측할 수 있습니다.
              <br />- 시도 횟수는 최초 맞춘 시점으로 고정되며, 최고 유사도만 업데이트됩니다.
              <br />- 다른 플레이어와 유사도를 경쟁하며 순위를 올려보세요!
            </p>
          </section>

          <section className="border-t-2 border-gray-200 dark:border-gray-800 pt-5 pb-5">
            <h3 className="text-base font-black text-gray-700 dark:text-gray-300 mb-2">유사도 색상</h3>
            <div className="flex h-3 border-2 border-black dark:border-white overflow-hidden">
              {Array.from({ length: 20 }, (_, i) => {
                const hue = (i / 19) * 120;
                return <div key={i} className="flex-1" style={{ backgroundColor: `hsl(${hue}, 80%, 45%)` }} />;
              })}
            </div>
            <div className="flex justify-between mt-1">
              <span className="text-[10px] font-bold text-gray-500">0% (멀어요)</span>
              <span className="text-[10px] font-bold text-gray-500">100% (정답!)</span>
            </div>
          </section>

          <section className="border-t-2 border-gray-200 dark:border-gray-800 pt-5">
            <h3 className="text-base font-black text-gray-700 dark:text-gray-300 mb-2">팁</h3>
            <ul className="space-y-1.5 text-sm font-medium">
              <li className="flex gap-2">
                <span className="shrink-0 font-black">·</span>
                <span>정답과 같은 주제, 같은 상황을 표현하는 문장을 시도해보세요.</span>
              </li>
              <li className="flex gap-2">
                <span className="shrink-0 font-black">·</span>
                <span>유사도가 오르는 방향으로 점진적으로 접근하세요.</span>
              </li>
              <li className="flex gap-2">
                <span className="shrink-0 font-black">·</span>
                <span>문장이 아니라 단어 단위로도 시도해보세요.</span>
              </li>
              <li className="flex gap-2">
                <span className="shrink-0 font-black">·</span>
                <span>익숙해진다면, 패턴이 보일지도..?</span>
              </li>
              <li className="flex gap-2">
                <span className="shrink-0 font-black">·</span>
                <span>매일 자정(KST)에 새로운 문장이 출제됩니다.</span>
              </li>
            </ul>
          </section>
        </div>
      </div>
    </div>
  );
}
