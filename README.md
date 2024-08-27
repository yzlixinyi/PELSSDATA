# A Profit Maximization Problem of Equipment Rental and Scheduling with Split Services

> This study addresses a scheduling problem arising from the ``Rent Instead of Buy'' business model where rental service platforms rent equipment from suppliers to satisfy customer requests in exchange for a lump-sum payment. Suppliers often implement piecewise linear concave charging policies to encourage longer rental duration. This policy prompts the platform to consolidate service periods into longer rental agreements for lower rental rates, potentially necessitating the splitting of a customer request among multiple facilities. We establish an integer programming model to maximize the platform’s profit, which is equal to the total revenue from serving customers minus the total rental and scheduling costs. To solve the problem, we propose two Dantzig-Wolfe decomposition reformulations and design branch-and-price algorithms accordingly. Furthermore, by exploiting the property of the concave rental cost functions, we derive the optimal structure regarding the start and end times of services, significantly reducing the subproblem’s search space. Numerical experiments based on real-life data validate the efficiency of the designed algorithms compared to commercial solvers and highlight the benefits of split services. Sensitivity analysis reveals that as the rental cost function becomes more segmented, more requests are split-serviced, but the times for splitting a request are seldom more than once.

*Keywords:* scheduling; resource renting; split service; profit maximizing; branch-and-price.

```
@article{
   author = {Li, Xinyi and Zhang, Canrong and Zhu, Jiarao},
   title = {A Profit Maximization Problem of Equipment Rental and Scheduling with Split Services},
   journal = {IISE Transactions},
   volume = {0},
   number = {ja},
   pages = {1--45},
   year = {2024},
   publisher = {Taylor \& Francis},
   type = {Journal Article}
}
```
