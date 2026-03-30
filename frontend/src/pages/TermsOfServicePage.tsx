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

export function TermsOfServicePage() {
  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-black">서비스 이용약관</h1>
        <p className="text-sm text-gray-600 dark:text-gray-400 font-medium">시행일: {EFFECTIVE_DATE}</p>
      </div>

      <Card className="space-y-8">
        <LegalSection title="제1조 (목적)">
          <p>
            본 약관은 「{SERVICE_NAME}」(이하 &ldquo;서비스&rdquo;)의 이용에 관한 조건, 운영자와 이용자 간의 권리·의무
            및 책임사항, 기타 필요한 사항을 규정함을 목적으로 합니다.
          </p>
        </LegalSection>

        <LegalSection title="제2조 (용어의 정의)">
          <p>본 약관에서 사용하는 용어의 정의는 다음과 같습니다.</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>
              <span className="font-bold">「서비스」:</span> {SERVICE_NAME}가 제공하는 AI 유사도 기반 문장 맞추기 게임
              및 부가 기능 일체
            </li>
            <li>
              <span className="font-bold">「이용자」:</span> 서비스에 접속하여 이용하는 모든 자
            </li>
            <li>
              <span className="font-bold">「회원」:</span> 가입 절차를 완료하여 계정을 보유한 이용자
            </li>
            <li>
              <span className="font-bold">「비회원」:</span> 가입 없이 서비스를 이용하는 자
            </li>
            <li>
              <span className="font-bold">「닉네임」:</span> 서비스 내에서 회원을 식별하기 위해 자동 생성되는 이름
            </li>
            <li>
              <span className="font-bold">「게임」:</span> 매일 제공되는 정답 문장을 추측하는 일일 문장 맞추기 게임
            </li>
          </ul>
        </LegalSection>

        <LegalSection title="제3조 (약관의 효력 및 변경)">
          <ul className="list-disc pl-6 space-y-1">
            <li>본 약관은 서비스 내에 게시함으로써 효력이 발생합니다.</li>
            <li>
              운영자는 합리적인 사유가 발생할 경우 관련 법령에 위배되지 않는 범위에서 약관을 변경할 수 있으며, 변경된
              약관은 적용일 7일 전에 공지합니다.
            </li>
            <li>이용자에게 불리한 약관 변경의 경우 최소 30일 전에 공지합니다.</li>
            <li>변경된 약관에 동의하지 않는 경우 회원 탈퇴를 통해 이용 계약을 해지할 수 있습니다.</li>
            <li>변경 공지 후 서비스를 계속 이용할 경우, 변경된 약관에 동의한 것으로 간주합니다.</li>
          </ul>
        </LegalSection>

        <LegalSection title="제4조 (서비스의 내용)">
          <p>서비스는 다음의 기능을 제공합니다.</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>일일 문장 추측 게임 (AI 유사도 측정 기반)</li>
            <li>실시간 랭킹 시스템</li>
            <li>프로필 관리 (닉네임 변경, 프로필 이미지 업로드)</li>
            <li>추측 히스토리 조회</li>
          </ul>
          <p>비회원도 게임에 참여할 수 있으나, 게임 기록 저장 및 랭킹 참여는 회원에 한합니다.</p>
          <p>서비스는 무료로 제공됩니다.</p>
        </LegalSection>

        <LegalSection title="제5조 (회원가입 및 탈퇴)">
          <ul className="list-disc pl-6 space-y-1">
            <li>
              <span className="font-bold">가입 방법:</span> 소셜 로그인(카카오, 구글, 네이버) 또는 아이디/비밀번호 직접
              가입
            </li>
            <li>만 14세 이상인 자에 한하여 가입할 수 있습니다.</li>
            <li>
              <span className="font-bold">탈퇴:</span> 마이페이지에서 직접 처리할 수 있습니다.
            </li>
            <li>탈퇴 시 개인정보는 즉시 삭제되며, 게임 기록은 익명화하여 통계 목적으로 보존됩니다.</li>
          </ul>
        </LegalSection>

        <LegalSection title="제6조 (이용자의 의무)">
          <p>이용자는 다음 각 호의 행위를 해서는 안 됩니다.</p>
          <ul className="list-disc pl-6 space-y-1">
            <li>자동화된 수단(봇, 스크립트 등)을 이용한 서비스 접근</li>
            <li>정답 문장을 부정한 방법으로 추출하는 행위</li>
            <li>타인의 계정 정보를 도용하거나 공유하는 행위</li>
            <li>금칙어 또는 부적절한 닉네임 사용</li>
            <li>서비스의 정상적인 운영을 방해하는 행위</li>
            <li>타인의 명예를 훼손하거나 권리를 침해하는 행위</li>
            <li>관련 법령에 위배되는 행위</li>
          </ul>
        </LegalSection>

        <LegalSection title="제7조 (서비스 이용 제한)">
          <ul className="list-disc pl-6 space-y-1">
            <li>
              <span className="font-bold">접속 빈도 제한(Rate Limiting):</span> 비정상적인 대량 요청 시 일시적으로
              이용이 제한될 수 있습니다.
            </li>
            <li>
              <span className="font-bold">계정 차단:</span> 제6조를 위반한 경우 관리자가 계정을 차단할 수 있습니다.
            </li>
            <li>차단된 계정은 로그인이 불가하며, 발급된 인증 토큰이 무효화됩니다.</li>
            <li>차단에 대한 이의 신청은 운영자 이메일({CONTACT_EMAIL})로 할 수 있습니다.</li>
          </ul>
        </LegalSection>

        <LegalSection title="제8조 (서비스의 변경 및 중단)">
          <ul className="list-disc pl-6 space-y-1">
            <li>서비스는 개인 프로젝트로 운영되며, 운영자는 서비스의 내용을 변경하거나 중단할 수 있습니다.</li>
            <li>변경 또는 중단 시 가능한 한 사전에 공지합니다.</li>
            <li>천재지변, 시스템 장애 등 불가항력적 사유로 인하여 사전 공지 없이 서비스가 중단될 수 있습니다.</li>
            <li>무료 서비스의 특성상 중단에 따른 별도의 보상은 하지 않습니다.</li>
          </ul>
        </LegalSection>

        <LegalSection title="제9조 (지적 재산권)">
          <ul className="list-disc pl-6 space-y-1">
            <li>서비스의 디자인, 코드, 콘텐츠에 대한 저작권은 운영자에게 귀속됩니다.</li>
            <li>이용자가 입력한 추측 문장은 유사도 측정 이외의 목적으로 수집·활용하지 않습니다.</li>
            <li>이용자는 서비스를 통해 얻은 정보를 운영자의 사전 동의 없이 상업적으로 이용할 수 없습니다.</li>
          </ul>
        </LegalSection>

        <LegalSection title="제10조 (면책 조항)">
          <ul className="list-disc pl-6 space-y-1">
            <li>서비스는 무료로 제공되며, 가용성이나 정확성을 보증하지 않습니다.</li>
            <li>AI 유사도 측정 결과는 근사치이며, 절대적인 기준이 아닙니다.</li>
            <li>이용자 상호 간의 분쟁에 대해 운영자는 개입 의무가 없습니다.</li>
            <li>이용자 본인의 귀책사유로 발생한 손해에 대해 운영자는 책임을 지지 않습니다.</li>
            <li>
              불가항력적 사유(서버 장애, 네트워크 문제, 외부 공격 등)로 인한 서비스 중단에 대해 운영자는 책임을 지지
              않습니다.
            </li>
          </ul>
        </LegalSection>

        <LegalSection title="제11조 (개인정보 보호)">
          <p>
            이용자의 개인정보는 「개인정보 처리방침」에 따라 처리됩니다. 상세 내용은{" "}
            <Link to="/privacy" className="font-bold text-indigo-600 dark:text-indigo-400 hover:underline">
              개인정보 처리방침
            </Link>
            을 참조하시기 바랍니다.
          </p>
        </LegalSection>

        <LegalSection title="제12조 (분쟁 해결)">
          <ul className="list-disc pl-6 space-y-1">
            <li>본 약관은 대한민국 법률에 따라 해석·적용됩니다.</li>
            <li>서비스 이용과 관련한 분쟁은 상호 합의에 의한 해결을 우선으로 합니다.</li>
            <li>합의가 이루어지지 않을 경우, 민사소송법에 따른 관할 법원에서 해결합니다.</li>
          </ul>
        </LegalSection>

        <section className="space-y-3">
          <h2 className="text-xl font-extrabold border-b-2 border-black dark:border-white pb-2">부칙</h2>
          <p className="text-base font-bold leading-relaxed">본 약관은 {EFFECTIVE_DATE}부터 시행됩니다.</p>
        </section>
      </Card>

      <div className="text-center pb-4">
        <Link to="/privacy" className="text-sm font-bold text-indigo-600 dark:text-indigo-400 hover:underline">
          개인정보 처리방침 보기
        </Link>
      </div>
    </div>
  );
}
