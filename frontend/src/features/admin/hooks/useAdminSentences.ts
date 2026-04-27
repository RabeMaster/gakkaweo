import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  checkDuplicate,
  createSentence,
  deleteSentence,
  emergencyReplace,
  getSentences,
  getSentenceStats,
  scheduleSentence,
  testSimilarity,
  unscheduleSentence,
  updateSentence,
  uploadCsv,
} from "@/features/admin/api";

export function useSentences(status?: string, sort?: string, page = 0, size = 20) {
  return useQuery({
    queryKey: ["admin", "sentences", status, sort, page, size],
    queryFn: () => getSentences(status, sort, page, size),
  });
}

export function useSentenceStats(publicId: string | null) {
  return useQuery({
    queryKey: ["admin", "sentences", publicId, "stats"],
    queryFn: () => getSentenceStats(publicId!),
    enabled: !!publicId,
  });
}

export function useCreateSentence() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sentence: string) => createSentence(sentence),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "sentences"] });
    },
  });
}

export function useUpdateSentence() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ publicId, sentence }: { publicId: string; sentence: string }) => updateSentence(publicId, sentence),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "sentences"] });
    },
  });
}

export function useDeleteSentence() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (publicId: string) => deleteSentence(publicId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "sentences"] });
    },
  });
}

export function useUploadCsv() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => uploadCsv(file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "sentences"] });
    },
  });
}

export function useScheduleSentence() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ publicId, date }: { publicId: string; date: string }) => scheduleSentence(publicId, date),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "sentences"] });
    },
  });
}

export function useUnscheduleSentence() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (publicId: string) => unscheduleSentence(publicId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "sentences"] });
    },
  });
}

export function useTestSimilarity() {
  return useMutation({
    mutationFn: ({ sentence, guessText }: { sentence: string; guessText: string }) =>
      testSimilarity(sentence, guessText),
  });
}

export function useCheckDuplicate() {
  return useMutation({
    mutationFn: (sentence: string) => checkDuplicate(sentence),
  });
}

export function useEmergencyReplace() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ newSentencePublicId, returnOldToPool }: { newSentencePublicId: string; returnOldToPool: boolean }) =>
      emergencyReplace(newSentencePublicId, returnOldToPool),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin"] });
    },
  });
}
