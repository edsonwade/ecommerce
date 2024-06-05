package code.with.vanilson;

import code.with.vanilson.customer.CustomerClient;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final CustomerClient customerClient;

    public OrderService(OrderRepository repository, CustomerClient customerClient, OrderMapper orderMapper) {
        this.repository = repository;
        this.customerClient = customerClient;
        this.orderMapper = orderMapper;
    }

    private final OrderMapper orderMapper;

//    @Transactional
//    public Integer createOrder(OrderRequest request) {
//        var customer = this.customerClient.findCustomerById(request.customerId())
//                .orElseThrow(() -> new BusinessException("Cannot create order:: No customer exists with the provided ID"));
//
//        var purchasedProducts = productClient.purchaseProducts(request.products());
//
//        var order = this.repository.save(mapper.toOrder(request));
//
//        for (PurchaseRequest purchaseRequest : request.products()) {
//            orderLineService.saveOrderLine(
//                    new OrderLineRequest(
//                            null,
//                            order.getId(),
//                            purchaseRequest.productId(),
//                            purchaseRequest.quantity()
//                    )
//            );
//        }
//        var paymentRequest = new PaymentRequest(
//                request.amount(),
//                request.paymentMethod(),
//                order.(),
//                order.getReference(),
//                customer
//        );
//        paymentClient.requestOrderPayment(paymentRequest);
//
//        orderProducer.sendOrderConfirmation(
//                new OrderConfirmation(
//                        request.reference(),
//                        request.amount(),
//                        request.paymentMethod(),
//                        customer,
//                        purchasedProducts
//                )
//        );
//
//        return order.getId();
//    }

    public List<OrderResponse> findAllOrders() {
        return this.repository.findAll()
                .stream()
                .map(this.orderMapper::fromOrder)
                .collect(Collectors.toList());
    }

    public OrderResponse findById(Integer id) {
        return this.repository.findById(id)
                .map(this.orderMapper::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("No order found with the provided ID: %d", id)));
    }
}
