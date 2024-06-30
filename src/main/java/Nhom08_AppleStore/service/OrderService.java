package Nhom08_AppleStore.service;

import Nhom08_AppleStore.model.*;
import Nhom08_AppleStore.repository.OrderDetailRepository;
import Nhom08_AppleStore.repository.OrderRepository;
import Nhom08_AppleStore.repository.RevenueRepository;
import Nhom08_AppleStore.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CartService cartService;
    private final RevenueRepository revenueRepository;
    private final VoucherRepository voucherRepository;

    public Order createOrder(String customerName, String address, String phoneNumber, String eMail,
                             String note, String payment, List<CartItem> cartItems) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        System.out.println("Khởi tạo đơn hàng cho: " + username);

        Order order = new Order();
        order.setCustomerName(customerName);
        order.setUsername(username);
        order.setAddress(address);
        order.setPhoneNumber(phoneNumber);
        order.setEMail(eMail);
        order.setNote(note);
        order.setPayment(payment);
        order.setDate(LocalDateTime.now());

        double totalPrice = 0;

        order = orderRepository.save(order);

        for (CartItem item : cartItems) {
            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(item.getProduct());
            detail.setQuantity(item.getQuantity());
            orderDetailRepository.save(detail);

            totalPrice += item.getProduct().getPrice() * item.getQuantity();
        }

        // Áp dụng giảm giá từ voucher nếu có
        double discountedPrice = totalPrice;
        Voucher appliedVoucher = cartService.getAppliedVoucher();
        if (appliedVoucher != null) {
            discountedPrice -= (discountedPrice * appliedVoucher.getDiscount() / 100);
            order.setVoucherCode(appliedVoucher.getCode()); // Lưu mã voucher vào đơn hàng
            appliedVoucher.decreaseQuantity(); // Giảm số lượng voucher
            voucherRepository.save(appliedVoucher); // Lưu lại voucher sau khi giảm số lượng
        }

        order.setTotalPrice(discountedPrice);
        order = orderRepository.save(order);

        cartService.clearCart();

        LocalDate orderDate = order.getDate().toLocalDate();
        Optional<Revenue> optionalRevenue = revenueRepository.findByDate(orderDate);

        if (optionalRevenue.isPresent()) {
            Revenue revenue = optionalRevenue.get();
            revenue.setTotalRevenue(revenue.getTotalRevenue() + order.getTotalPrice());
            revenue.setNumberOfSales(revenue.getNumberOfSales() + 1);
            revenueRepository.save(revenue);
        } else {
            Revenue newRevenue = new Revenue();
            newRevenue.setDate(orderDate);
            newRevenue.setTotalRevenue(order.getTotalPrice());
            newRevenue.setNumberOfSales(1);
            revenueRepository.save(newRevenue);
        }

        return order;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getOrdersByUsername(String username) {
        return orderRepository.findByUsername(username);
    }
}
