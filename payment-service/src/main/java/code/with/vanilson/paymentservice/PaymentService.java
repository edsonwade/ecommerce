package code.with.vanilson.paymentservice;

import code.with.vanilson.paymentservice.notification.NotificationProducer;
import code.with.vanilson.paymentservice.notification.PaymentNotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper mapper;
    private final NotificationProducer notificationProducer;

    public Integer createPayment(PaymentRequest request) {
        var payment = this.repository.save(this.mapper.toPayment(request));

        this.notificationProducer.sendNotification(
                new PaymentNotificationRequest(
                        request.orderReference(),
                        request.amount(),
                        request.paymentMethod(),
                        request.customer().getFirstname(),
                        request.customer().getLastname(),
                        request.customer().getEmail()
                )
        );
        return payment.getPaymentId();
    }
}