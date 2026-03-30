import { Link, useLocation } from "react-router-dom";

const TABS: { path: string; label: string; icon: string }[] = [
  { path: "dashboard", label: "대시보드", icon: "■" },
  { path: "sentences", label: "문장 관리", icon: "▤" },
  { path: "users", label: "사용자 관리", icon: "▦" },
  { path: "system", label: "시스템", icon: "⚙" },
];

export function AdminSidebar() {
  const { pathname } = useLocation();

  return (
    <nav className="w-56 shrink-0 border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal">
      <div className="border-b-4 border-black dark:border-white px-5 py-4">
        <h2 className="text-lg font-black tracking-tight">관리 패널</h2>
      </div>
      <ul className="py-2">
        {TABS.map((tab) => {
          const isActive = pathname.startsWith(`/admin/${tab.path}`);
          return (
            <li key={tab.path}>
              <Link
                to={`/admin/${tab.path}`}
                className={[
                  "block w-full text-left px-5 py-3 font-bold text-sm transition-colors duration-100 flex items-center gap-3",
                  isActive
                    ? "bg-yellow-300 text-black"
                    : "text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 hover:text-black dark:hover:text-white",
                ].join(" ")}
              >
                <span className="text-base leading-none">{tab.icon}</span>
                {tab.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
