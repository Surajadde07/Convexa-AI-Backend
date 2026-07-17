package com.convexa.ai.convexa_ai_backend.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendInvitationEmail(String toEmail, String companyName, String role, String token) {
        sendInvitationEmail(toEmail, companyName, null, role, null, "A Manager", token, "48 hours");
    }

    public void sendInvitationEmail(
            String toEmail,
            String companyName,
            String companyLogo,
            String role,
            String department,
            String managerName,
            String token,
            String expiresAtStr
    ) {
        String logoUrl = (companyLogo != null && !companyLogo.isBlank()) ? companyLogo : "https://via.placeholder.com/200x60/8b5cf6/ffffff?text=Convexa+AI";
        String dept = (department != null && !department.isBlank()) ? department : "General / Sales";
        String inviteUrl = "http://localhost:5173/invite/" + token;

        String htmlContent = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"utf-8\">\n" +
                "    <title>Invitation to Join Convexa AI</title>\n" +
                "    <style>\n" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; background-color: #0b0f19; color: #f8fafc; margin: 0; padding: 0; }\n" +
                "        .container { max-width: 580px; margin: 0 auto; padding: 40px 20px; background-color: #0b0f19; }\n" +
                "        .card { background-color: rgba(255, 255, 255, 0.03); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 16px; padding: 32px; text-align: center; }\n" +
                "        .logo { max-height: 48px; margin-bottom: 24px; border-radius: 8px; }\n" +
                "        .title { font-size: 20px; font-weight: 800; color: #ffffff; margin-bottom: 8px; }\n" +
                "        .subtitle { font-size: 14px; color: #94a3b8; margin-bottom: 24px; }\n" +
                "        .details-box { background-color: rgba(255, 255, 255, 0.02); border: 1px solid rgba(255, 255, 255, 0.05); border-radius: 12px; padding: 20px; text-align: left; margin-bottom: 28px; }\n" +
                "        .detail-row { display: flex; justify-content: space-between; margin-bottom: 12px; font-size: 13px; }\n" +
                "        .detail-row:last-child { margin-bottom: 0; }\n" +
                "        .detail-label { color: #64748b; font-weight: 600; }\n" +
                "        .detail-val { color: #e2e8f0; font-weight: 700; }\n" +
                "        .btn { display: inline-block; background: linear-gradient(135deg, #7c3aed, #2563eb); color: #ffffff !important; font-size: 13px; font-weight: 700; padding: 12px 28px; border-radius: 12px; text-decoration: none; box-shadow: 0 4px 12px rgba(124, 58, 237, 0.3); margin-bottom: 24px; }\n" +
                "        .footer { font-size: 11px; color: #475569; margin-top: 32px; border-top: 1px solid rgba(255, 255, 255, 0.06); padding-top: 20px; }\n" +
                "        .footer a { color: #8b5cf6; text-decoration: none; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"card\">\n" +
                "            <img src=\"" + logoUrl + "\" alt=\"" + companyName + " Logo\" class=\"logo\">\n" +
                "            <h1 class=\"title\">Join " + companyName + " on Convexa AI</h1>\n" +
                "            <p class=\"subtitle\">" + managerName + " has invited you to collaborate in their workspace.</p>\n" +
                "            \n" +
                "            <div class=\"details-box\">\n" +
                "                <div class=\"detail-row\">\n" +
                "                    <span class=\"detail-label\">Email:</span>\n" +
                "                    <span class=\"detail-val\">" + toEmail + "</span>\n" +
                "                </div>\n" +
                "                <div class=\"detail-row\">\n" +
                "                    <span class=\"detail-label\">Workspace:</span>\n" +
                "                    <span class=\"detail-val\">" + companyName + "</span>\n" +
                "                </div>\n" +
                "                <div class=\"detail-row\">\n" +
                "                    <span class=\"detail-label\">Role:</span>\n" +
                "                    <span class=\"detail-val\">" + role + "</span>\n" +
                "                </div>\n" +
                "                <div class=\"detail-row\">\n" +
                "                    <span class=\"detail-label\">Department:</span>\n" +
                "                    <span class=\"detail-val\">" + dept + "</span>\n" +
                "                </div>\n" +
                "            </div>\n" +
                "\n" +
                "            <a href=\"" + inviteUrl + "\" class=\"btn\" target=\"_blank\">Accept Invitation</a>\n" +
                "\n" +
                "            <p style=\"font-size: 11px; color: #64748b; margin-top: 15px; word-break: break-all;\">\n" +
                "                Or copy this link:<br>\n" +
                "                <a href=\"" + inviteUrl + "\" style=\"color: #8b5cf6; text-decoration: none;\">" + inviteUrl + "</a>\n" +
                "            </p>\n" +
                "\n" +
                "            <p style=\"font-size: 11px; color: #64748b; margin-top: 10px;\">This invitation link will expire on " + expiresAtStr + ".</p>\n" +
                "\n" +
                "            <div class=\"footer\">\n" +
                "                <p>This message was sent from Convexa AI on behalf of " + companyName + ".</p>\n" +
                "                <p>Questions? Contact our support team at <a href=\"mailto:support@convexa.ai\">support@convexa.ai</a>.</p>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        System.out.println("====== [MOCK EMAIL SENT] ======");
        System.out.println("To: " + toEmail);
        System.out.println("Subject: Invitation to join " + companyName + " on Convexa AI");
        System.out.println("Link: " + inviteUrl);
        System.out.println("=============================");

        if (mailSender != null) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setTo(toEmail);
                helper.setSubject("Invitation to join " + companyName + " on Convexa AI");
                helper.setText(htmlContent, true);
                mailSender.send(mimeMessage);
            } catch (Exception e) {
                System.err.println("Failed to send HTML email via SMTP: " + e.getMessage());
            }
        }
    }
}
