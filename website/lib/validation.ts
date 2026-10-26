import { z } from "zod";

/** Shared form schemas. Server-side rules in the backend remain the source of truth. */

export const contactSchema = z.object({
  name: z.string().trim().min(2, "Please enter your name").max(120),
  email: z.email("Enter a valid email address"),
  company: z.string().trim().max(160).optional().or(z.literal("")),
  topic: z.enum(["demo", "question", "support", "other"], { message: "Choose a topic" }),
  message: z.string().trim().min(10, "Tell us a little more (at least 10 characters)").max(2000),
});
export type ContactInput = z.infer<typeof contactSchema>;

export const loginSchema = z.object({
  email: z.email("Enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

const password = z.string().min(8, "Password must be at least 8 characters").max(100);

export const registerBusinessSchema = z.object({
  businessName: z.string().trim().min(1, "Business name is required").max(200),
  businessType: z.string().trim().max(120).optional(),
  businessPhone: z.string().trim().max(50).optional(),
  city: z.string().trim().max(120).optional(),
  country: z.string().trim().max(120).optional(),
});

export const registerAdminSchema = z.object({
  fullName: z.string().trim().min(1, "Your name is required").max(200),
  email: z.email("Enter a valid email address"),
  phone: z.string().trim().max(50).optional(),
  password,
});

export const acceptInvitationSchema = z.object({
  code: z.string().trim().min(1, "Invitation code is required"),
  fullName: z.string().trim().max(200).optional(),
  password,
});

export const partySchema = z.object({
  name: z.string().trim().min(1, "Name is required").max(200),
  phone: z.string().trim().max(50).optional(),
  email: z.union([z.literal(""), z.email("Email is invalid")]).optional(),
  address: z.string().trim().max(500).optional(),
  taxId: z.string().trim().max(50).optional(),
  notes: z.string().trim().max(1000).optional(),
});

/** Flattens zod issues into { field: message } like the backend's fieldErrors. */
export function fieldErrorsOf(error: z.ZodError): Record<string, string> {
  const out: Record<string, string> = {};
  for (const issue of error.issues) {
    const key = issue.path.join(".");
    if (!out[key]) out[key] = issue.message;
  }
  return out;
}
