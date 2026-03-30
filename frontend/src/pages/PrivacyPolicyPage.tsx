import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { Card } from "@/shared/ui/Card";

const EFFECTIVE_DATE = "2026년 4월 1일";
const SERVICE_NAME = "가까워";
const CONTACT_EMAIL = "wnsgh5462@gmail.com";

function LegalSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-xl font-extrabold border-b-2 border-black dark:border-white pb-2">{title}</h2>
      <div className="space-y-2 text-base font-medium leading-relaxed">{children}</div>
    </section>
  );
}

export function PrivacyPolicyPage() {
  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-black">개인정보 처리방침</h1>
        <p className="text-sm text-gray-600 dark:text-gray-400 font-medium">시행일: {EFFECTIVE_DATE}</p>
      </div>

      <Card className="space-y-8">
        <LegalSection title="제1조 (개인정보의 처리 목적)">
          <p>
            「{SERVICE_NAME}」(이하 &ldquo;서비스&rdquo;)는 개인정보를 다음의 목적을 위해 처리합니다. 처리하고 있는
            개인정보는 다음 목적 이외의 용도로는 이용되지 않으며, 이용 목적이 변경되는 경우에는 별도의 동의를 받는 등
            필요한 조치를 이행할 예정입니다.
          </p>
          <ul className="list-disc pl-6 space-y-1">
            <li>회원 가입 및 관리: 회원 식별, 로그인 인증, 계정 관리</li>
            <li>서비스 제공: 게임 기록 저장, 랭킹 참여, 프로필 관리</li>
            <li>서비스 안정성 확보: 악용 방지, 비정상 이용 탐지, 접속 빈도 제한(Rate Limiting)</li>
          </ul>
        </LegalSection>

        <LegalSection title="제2조 (수집하는 개인정보 항목 및 수집 방법)">
          <p>서비스는 다음과 같은 개인정보를 수집합니다.</p>
          <div className="overflow-x-auto">
            <table className="w-full border-2 border-black dark:border-white text-sm">
              <thead>
                <tr className="bg-gray-100 dark:bg-gray-800">
                  <th className="border-b-2 border-r border-black dark:border-white px-3 py-2 text-left font-extrabold">
                    구분
                  </th>
                  <th className="border-b-2 border-r border-black dark:border-white px-3 py-2 text-left font-extrabold">
                    항목
                  </th>
                  <th className="border-b-2 border-r border-black dark:border-white px-3 py-2 text-left font-extrabold">
                    수집 방법
                  </th>
                  <th className="border-b-2 border-black dark:border-white px-3 py-2 text-left font-extrabold">목적</th>
                </tr>
              </thead>
              <tbody>
                <tr className="border-b border-black/20 dark:border-white/20">
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">
                    소셜 로그인
                    <br />
                    (카카오/구글/네이버)
                  </td>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">
                    소셜 계정 식별자, 이메일(선택)
                  </td>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">
                    OAuth 인증 과정에서 자동 수집
                  </td>
                  <td className="px-3 py-2">로그인 식별</td>
                </tr>
                <tr className="border-b border-black/20 dark:border-white/20">
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">{SERVICE_NAME} 계정</td>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">아이디, 비밀번호</td>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">회원가입 시 직접 입력</td>
                  <td className="px-3 py-2">
                    로그인 식별
                    <br />
                    (비밀번호는 BCrypt 암호화 저장)
                  </td>
                </tr>
                <tr className="border-b border-black/20 dark:border-white/20">
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">서비스 이용</td>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">IP 주소</td>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">서비스 이용 시 자동 수집</td>
                  <td className="px-3 py-2">악용 방지, Rate Limiting</td>
                </tr>
                <tr>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">사용자 제공 콘텐츠</td>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">프로필 이미지</td>
                  <td className="border-r border-black/20 dark:border-white/20 px-3 py-2">
                    마이페이지에서 직접 업로드
                  </td>
                  <td className="px-3 py-2">프로필 표시</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            ※ 닉네임은 서버가 자동 생성하는 무작위 조합으로, 개인정보에 해당하지 않습니다.
          </p>
        </LegalSection>

        <LegalSection title="제3조 (개인정보의 처리 및 보유 기간)">
          <p>
            서비스는 법령에 따른 개인정보 보유·이용 기간 또는 정보주체로부터 개인정보를 수집 시 동의받은 개인정보
            보유·이용 기간 내에서 개인정보를 처리·보유합니다.
          </p>
          <ul className="list-disc pl-6 space-y-1">
            <li>
              <span className="font-bold">회원 정보:</span> 회원 탈퇴 시까지 (탈퇴 즉시 파기)
            </li>
            <li>
              <span className="font-bold">게임 기록:</span> 회원 탈퇴 시 개인식별 정보를 삭제하고 익명화하여 통계
              목적으로 보존
            </li>
            <li>
              <span className="font-bold">IP 주소 로그:</span> 수집일로부터 30일 후 자동 삭제
            </li>
            <li>
              <span className="font-bold">접속 빈도 제한 데이터:</span> Redis TTL에 의해 자동 만료 (최대 1분)
            </li>
          </ul>
          <p>관련 법령에 따라 보존이 필요한 경우:</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>표시·광고에 관한 기록: 6개월 (전자상거래 등에서의 소비자보호에 관한 법률)</li>
            <li>서비스 이용 관련 분쟁 기록: 3년 (전자상거래 등에서의 소비자보호에 관한 법률)</li>
          </ul>
        </LegalSection>

        <LegalSection title="제4조 (개인정보의 제3자 제공)">
          <p>
            서비스는 원칙적으로 이용자의 개인정보를 제3자에게 제공하지 않습니다. 다만, 다음의 경우에는 예외로 합니다.
          </p>
          <ul className="list-disc pl-6 space-y-1">
            <li>이용자가 사전에 동의한 경우</li>
            <li>
              법령의 규정에 의거하거나, 수사 목적으로 법령에 정해진 절차와 방법에 따라 수사기관의 요구가 있는 경우
            </li>
          </ul>
        </LegalSection>

        <LegalSection title="제5조 (개인정보 처리의 위탁)">
          <p>
            서비스는 현재 개인정보 처리를 외부 업체에 위탁하지 않습니다. 향후 위탁이 필요한 경우 본 방침을 통해 사전에
            고지하겠습니다.
          </p>
        </LegalSection>

        <LegalSection title="제6조 (정보주체의 권리·의무 및 행사 방법)">
          <p>이용자는 개인정보주체로서 다음과 같은 권리를 행사할 수 있습니다.</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>
              <span className="font-bold">개인정보 열람 요구:</span> 마이페이지에서 확인
            </li>
            <li>
              <span className="font-bold">개인정보 정정·삭제 요구:</span> 닉네임 변경, 프로필 이미지 변경 및 삭제
            </li>
            <li>
              <span className="font-bold">개인정보 처리 정지 요구</span>
            </li>
            <li>
              <span className="font-bold">회원 탈퇴:</span> 마이페이지에서 직접 처리 (개인정보 즉시 삭제)
            </li>
          </ul>
          <p>
            위 권리는 이메일({CONTACT_EMAIL})을 통해서도 행사할 수 있으며, 서비스는 이에 대해 지체 없이 조치하겠습니다.
          </p>
          <p>서비스는 만 14세 미만 아동의 개인정보를 수집하지 않습니다.</p>
        </LegalSection>

        <LegalSection title="제7조 (개인정보의 파기 절차 및 방법)">
          <p>
            서비스는 개인정보 보유 기간의 경과, 처리 목적 달성 등 개인정보가 불필요하게 되었을 때에는 지체 없이 해당
            개인정보를 파기합니다.
          </p>
          <p className="font-bold">파기 방법:</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>데이터베이스 기록: 영구 삭제</li>
            <li>파일(프로필 이미지): 서버 파일시스템에서 삭제</li>
            <li>게임 기록: 회원 식별 정보를 삭제하여 익명화 (통계 보존)</li>
            <li>Redis 데이터: 키 삭제 (랭킹, 토큰 블랙리스트 등)</li>
          </ul>
        </LegalSection>

        <LegalSection title="제8조 (쿠키 및 자동 수집 장치의 설치·운영·거부)">
          <p>서비스는 다음과 같은 쿠키 및 저장 장치를 사용합니다.</p>
          <p className="font-bold">쿠키:</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>
              <span className="font-bold">access_token:</span> 로그인 인증용 JWT (HttpOnly, 브라우저 JavaScript 접근
              불가)
            </li>
            <li>
              <span className="font-bold">refresh_token:</span> 토큰 갱신용 JWT (HttpOnly)
            </li>
            <li>
              <span className="font-bold">has_session:</span> 로그인 상태 표시용 (비인증 정보, 성능 최적화 목적)
            </li>
          </ul>
          <p className="font-bold">localStorage 저장 항목:</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>테마 설정 (라이트/다크 모드)</li>
            <li>공지 닫기 기록</li>
          </ul>
          <p>
            이용자는 브라우저 설정을 통해 쿠키 저장을 거부할 수 있습니다. 다만, 쿠키를 차단할 경우 로그인 기능 이용이
            제한됩니다.
          </p>
        </LegalSection>

        <LegalSection title="제9조 (개인정보의 안전성 확보 조치)">
          <p>서비스는 개인정보의 안전성 확보를 위해 다음과 같은 조치를 취하고 있습니다.</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>
              <span className="font-bold">비밀번호 암호화:</span> BCrypt 해시 알고리즘 적용 (원문 복원 불가)
            </li>
            <li>
              <span className="font-bold">전송 구간 암호화:</span> HTTPS(TLS) 적용
            </li>
            <li>
              <span className="font-bold">쿠키 보안:</span> HttpOnly 속성으로 XSS 공격 방지
            </li>
            <li>
              <span className="font-bold">토큰 보안:</span> Refresh Token Rotation 적용, 탈취 감지 시 전체 세션 무효화
            </li>
            <li>
              <span className="font-bold">접근 제한:</span> 관리자 권한 분리 (ROLE_ADMIN)
            </li>
          </ul>
        </LegalSection>

        <LegalSection title="제10조 (개인정보 보호책임자)">
          <p>
            서비스는 개인정보 처리에 관한 업무를 총괄해서 책임지는 개인정보 보호책임자를 다음과 같이 지정하고 있습니다.
          </p>
          <ul className="list-disc pl-6 space-y-1">
            <li>
              <span className="font-bold">성명:</span> {SERVICE_NAME} 운영자
            </li>
            <li>
              <span className="font-bold">이메일:</span> {CONTACT_EMAIL}
            </li>
          </ul>
          <p>개인정보 관련 문의, 불만 처리, 피해 구제 등에 관한 사항은 위 연락처로 문의하실 수 있습니다.</p>
        </LegalSection>

        <LegalSection title="제11조 (권익침해 구제 방법)">
          <p>
            이용자는 개인정보 침해로 인한 구제를 받기 위하여 다음 기관에 분쟁 해결이나 상담 등을 신청할 수 있습니다.
          </p>
          <ul className="list-disc pl-6 space-y-1">
            <li>개인정보침해신고센터 (국번없이 118, privacy.kisa.or.kr)</li>
            <li>개인정보분쟁조정위원회 (1833-6972, kopico.go.kr)</li>
            <li>대검찰청 사이버수사과 (국번없이 1301, spo.go.kr)</li>
            <li>경찰청 사이버수사국 (국번없이 182, ecrm.police.go.kr)</li>
          </ul>
        </LegalSection>

        <LegalSection title="제12조 (개인정보 처리방침의 변경)">
          <p>
            본 개인정보 처리방침이 변경되는 경우, 서비스 내 공지사항을 통해 변경 사항을 사전에 고지합니다. 변경된 방침은
            시행일 7일 전에 공지하며, 이용자의 권리에 중대한 변경이 있는 경우에는 최소 30일 전에 공지합니다.
          </p>
          <p className="font-bold">본 개인정보 처리방침은 {EFFECTIVE_DATE}부터 시행됩니다.</p>
        </LegalSection>
      </Card>

      <div className="text-center pb-4">
        <Link to="/terms" className="text-sm font-bold text-indigo-600 dark:text-indigo-400 hover:underline">
          서비스 이용약관 보기
        </Link>
      </div>
    </div>
  );
}
