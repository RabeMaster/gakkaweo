import { render, screen } from "@testing-library/react";
import { DesktopOnlyGate } from "@/shared/ui/DesktopOnlyGate";

function mockMatchMedia(matches: boolean) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

describe("DesktopOnlyGate", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    document.head.querySelectorAll('meta[name="robots"]').forEach((el) => el.remove());
  });

  it("모바일에서는 안내 메시지를 렌더하고 children을 렌더하지 않는다", () => {
    mockMatchMedia(false);

    render(
      <DesktopOnlyGate>
        <div data-testid="child">멀티플레이</div>
      </DesktopOnlyGate>,
    );

    expect(screen.getByText("데스크톱에서 이용해주세요")).toBeInTheDocument();
    expect(screen.queryByTestId("child")).not.toBeInTheDocument();
  });

  it("데스크톱에서는 children을 렌더하고 안내 메시지를 렌더하지 않는다", () => {
    mockMatchMedia(true);

    render(
      <DesktopOnlyGate>
        <div data-testid="child">멀티플레이</div>
      </DesktopOnlyGate>,
    );

    expect(screen.getByTestId("child")).toBeInTheDocument();
    expect(screen.queryByText("데스크톱에서 이용해주세요")).not.toBeInTheDocument();
  });

  it("noindex meta 태그를 삽입한다", () => {
    mockMatchMedia(true);

    render(
      <DesktopOnlyGate>
        <div>콘텐츠</div>
      </DesktopOnlyGate>,
    );

    const meta = document.head.querySelector('meta[name="robots"]');
    expect(meta).not.toBeNull();
    expect(meta?.getAttribute("content")).toBe("noindex,nofollow");
  });
});
