type Tab = "dashboard" | "sentences" | "users" | "system";

interface AdminSidebarProps {
  activeTab: Tab;
  onTabChange: (tab: Tab) => void;
}

const TABS: { id: Tab; label: string; icon: string }[] = [
  { id: "dashboard", label: "대시보드", icon: "■" },
  { id: "sentences", label: "문장 관리", icon: "▤" },
  { id: "users", label: "사용자 관리", icon: "▦" },
  { id: "system", label: "시스템", icon: "⚙" },
];

export function AdminSidebar({ activeTab, onTabChange }: AdminSidebarProps) {
  return (
    <nav className="w-56 shrink-0 border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal">
      <div className="border-b-4 border-black dark:border-white px-5 py-4">
        <h2 className="text-lg font-black tracking-tight">관리 패널</h2>
      </div>
      <ul className="py-2">
        {TABS.map((tab) => (
          <li key={tab.id}>
            <button
              type="button"
              onClick={() => onTabChange(tab.id)}
              className={[
                "w-full text-left px-5 py-3 font-bold text-sm transition-colors duration-100 flex items-center gap-3",
                activeTab === tab.id
                  ? "bg-yellow-300 text-black"
                  : "text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 hover:text-black dark:hover:text-white",
              ].join(" ")}
            >
              <span className="text-base leading-none">{tab.icon}</span>
              {tab.label}
            </button>
          </li>
        ))}
      </ul>
    </nav>
  );
}
