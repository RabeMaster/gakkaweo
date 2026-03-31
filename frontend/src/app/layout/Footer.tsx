import { Link } from "react-router-dom";

const LEGAL_LINKS = [
  { label: "이용약관", to: "/terms" },
  { label: "개인정보처리방침", to: "/privacy" },
] as const;

const EXTERNAL_LINKS = [
  { label: "버그제보/피드백", href: "https://forms.gle/UjqHjVuyJbpchooT6" },
  { label: "이메일", href: "mailto:wnsgh5462@gmail.com" },
  { label: "GitHub", href: "https://github.com/RabeMaster/gakkaweo" },
  { label: "Blog", href: "https://r4b2.tistory.com/" },
] as const;

const linkClassName =
  "text-sm font-bold text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white transition-colors";

export function Footer() {
  return (
    <footer className="border-t-4 border-black dark:border-white bg-white dark:bg-gray-950">
      <div className="max-w-6xl mx-auto px-6 py-6 flex items-center justify-between">
        <div className="flex items-center gap-4">
          <span className="text-sm font-bold text-gray-600 dark:text-gray-400">© 2026 가까워</span>
          {LEGAL_LINKS.map((link) => (
            <Link key={link.to} to={link.to} className={linkClassName}>
              {link.label}
            </Link>
          ))}
        </div>

        <nav className="flex items-center gap-4">
          {EXTERNAL_LINKS.map((link) => (
            <a key={link.href} href={link.href} target="_blank" rel="noopener noreferrer" className={linkClassName}>
              {link.label}
            </a>
          ))}
        </nav>
      </div>
    </footer>
  );
}
