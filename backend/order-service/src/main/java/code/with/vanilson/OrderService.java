package code.with.vanilson;

import code.with.vanilson.orderLine.OrderLineRequest;
import code.with.vanilson.orderLine.OrderLineService;
import code.with.vanilson.productservice.ProductClient;
import code.with.vanilson.productservice.except.BusinessException;
import code.with.vanilson.productservice.purchase.PurchaseRequest;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderLineService orderLineService;

    public OrderService(OrderRepository orderRepository, CustomerClient customerClient, ProductClient productClient,
                        OrderLineService orderLineService,
                        OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.customerClient = customerClient;
        this.productClient = productClient;
        this.orderLineService = orderLineService;
        this.orderMapper = orderMapper;
    }

    private final OrderMapper orderMapper;

    @Transactional
    public Integer createOrder(OrderRequest request) {
        var customer = this.customerClient.findCustomerById(request.customerId())
                .orElseThrow(
                        () -> new BusinessException("Cannot create order:: No customer exists with the provided ID"));

        // purchase the products (RestTemplate)

        var purchasedProducts = productClient.purchaseProducts(request.products());

        var order = this.orderRepository.save(orderMapper.toOrder(request));

        for (PurchaseRequest purchaseRequest : request.products()) {
            orderLineService.saveOrderLine(
                    new OrderLineRequest(
                            null,
                            order.getOrderId(),
                            purchaseRequest.productId(),
                            purchaseRequest.quantity()
                    )
            );
        }
////        var paymentRequest = new PaymentRequest(
////                request.amount(),
////                request.paymentMethod(),
////                order.(),
////                order.getReference(),
////                customer
////        );
////        paymentClient.requestOrderPayment(paymentRequest);
////
////        orderProducer.sendOrderConfirmation(
////                new OrderConfirmation(
////                        request.reference(),
////                        request.amount(),
////                        request.paymentMethod(),
////                        customer,
////                        purchasedProducts
////                )
////        );
////
////        return order.getId();
        return null;
    }

    public List<OrderResponse> findAllOrders() {
        return this.orderRepository.findAll()
                .stream()
                .map(this.orderMapper::fromOrder)
                .collect(Collectors.toList());
    }

    public OrderResponse findById(Integer id) {
        return this.orderRepository.findById(id)
                .map(this.orderMapper::fromOrder)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("No order found with the provided ID: %d", id)));
    }
}
