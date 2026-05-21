class SearchRequest {
  final String name;
  final String? company;
  final String? title;

  SearchRequest({required this.name, this.company, this.title});

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'company': company,
      'title': title,
    };
  }
}
